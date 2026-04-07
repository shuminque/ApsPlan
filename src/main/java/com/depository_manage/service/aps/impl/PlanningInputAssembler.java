package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ProductionOrderStatus;
import com.depository_manage.entity.aps.ProductionLineRuntime;
import com.depository_manage.entity.aps.RuntimeStatus;
import com.depository_manage.entity.aps.SafetyStock;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.service.aps.ProductionLineRuntimeService;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.service.aps.planning.NormalizedPlanningRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class PlanningInputAssembler {

    private static final BigDecimal DEFAULT_SHIFT_HOURS = new BigDecimal("8");
    private static final long CAPACITY_BUFFER_DAYS = 180L;

    private final ProductionOrderService productionOrderService;
    private final SafetyStockService safetyStockService;
    private final ShiftCalendarService shiftCalendarService;
    private final ProductionLineModelConfigMapper modelConfigMapper;
    private final ProductionLineMapper productionLineMapper;
    private final ProductionLineRuntimeService productionLineRuntimeService;
    private final BearingRecordService bearingRecordService;
    private final ZoneId zoneId;

    PlanningInputAssembler(ProductionOrderService productionOrderService,
                           SafetyStockService safetyStockService,
                           ShiftCalendarService shiftCalendarService,
                           ProductionLineModelConfigMapper modelConfigMapper,
                           ProductionLineMapper productionLineMapper,
                           ProductionLineRuntimeService productionLineRuntimeService,
                           BearingRecordService bearingRecordService,
                           ZoneId zoneId) {
        this.productionOrderService = productionOrderService;
        this.safetyStockService = safetyStockService;
        this.shiftCalendarService = shiftCalendarService;
        this.modelConfigMapper = modelConfigMapper;
        this.productionLineMapper = productionLineMapper;
        this.productionLineRuntimeService = productionLineRuntimeService;
        this.bearingRecordService = bearingRecordService;
        this.zoneId = zoneId;
    }

    PlanningSnapshot assemble(NormalizedPlanningRequest normalizedRequest) {
        List<String> orderStatusFilters = resolveOrderStatusFilters(normalizedRequest.getMode());
        List<ProductionOrder> openOrders = productionOrderService.list(new LambdaQueryWrapper<ProductionOrder>()
                .in(ProductionOrder::getStatus, orderStatusFilters)
                .gt(ProductionOrder::getQuantity, 0));
        if (openOrders.isEmpty()) {
            return new PlanningSnapshot(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap());
        }

        List<SafetyStock> safetyStocks = safetyStockService.list();
        Map<String, SafetyStock> safetyStockByKey = safetyStocks.stream()
                .filter(s -> s.getCustomer() != null && s.getModel() != null && s.getOuterInnerRing() != null)
                .collect(Collectors.toMap(this::toKey, s -> s, (a, b) -> b));

        Map<String, List<ProductionOrder>> orderByKey = openOrders.stream()
                .filter(o -> o.getCustomer() != null && o.getModel() != null && o.getOuterInnerRing() != null)
                .collect(Collectors.groupingBy(this::toKey));

        LocalDate start = normalizedRequest.getStart();
        LocalDate endExclusive = normalizedRequest.getEndExclusive();
        LocalDateTime effectiveStartAt = normalizedRequest.getEffectiveStartAt();
        Map<String, Integer> currentInventoryByKey = queryCurrentInventoryByKey(start, orderByKey);

        LocalDate planningCapacityEndExclusive = endExclusive.plusDays(CAPACITY_BUFFER_DAYS);
        Map<LocalDate, BigDecimal> shiftHoursByDay = buildShiftHours(start, planningCapacityEndExclusive, effectiveStartAt);

        List<ProductionLineModelConfig> lineModelConfigs = modelConfigMapper.selectPageList(null, null, 0L, 2000L);
        List<ProductionLine> productionLines = productionLineMapper.selectPageList(null, 0L, 1000L);
        Map<String, List<LineCapacity>> lineCapByModel = buildModelCapacities(
                normalizedRequest.getScopedLineIds(), lineModelConfigs, productionLines);
        Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId = buildRuntimeViewByLineId(
                normalizedRequest.getScopedLineIds());
        Map<LineDayKey, Integer> remainingCapacityByLineDay = buildRemainingCapacityByLineDay(
                start, planningCapacityEndExclusive, shiftHoursByDay, lineCapByModel, runtimeViewByLineId);

        return new PlanningSnapshot(openOrders, safetyStocks, orderByKey, safetyStockByKey, currentInventoryByKey,
                shiftHoursByDay, lineModelConfigs, productionLines, lineCapByModel, remainingCapacityByLineDay,
                runtimeViewByLineId);
    }

    private List<String> resolveOrderStatusFilters(String mode) {
        return ProductionOrderStatus.aliasesFor(ProductionOrderStatus.PENDING.getCode());
    }

    private Map<LocalDate, BigDecimal> buildShiftHours(LocalDate start, LocalDate endExclusive, LocalDateTime planStartAt) {
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        BigDecimal fallbackShiftHours = resolveFallbackShiftHours(start, endExclusive, planStartAt);
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            List<ShiftSchedule> schedules = getSchedulesImpactingDay(cursor);
            BigDecimal hours = calcDailyShiftHours(cursor, schedules, planStartAt);
            boolean isCustomStartDay = planStartAt != null
                    && cursor.equals(planStartAt.toLocalDate())
                    && !planStartAt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
            if (hours.compareTo(BigDecimal.ZERO) <= 0 && !isCustomStartDay) {
                hours = fallbackShiftHours;
            }
            result.put(cursor, hours);
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private BigDecimal resolveFallbackShiftHours(LocalDate start, LocalDate endExclusive, LocalDateTime planStartAt) {
        BigDecimal inferred = inferShiftHoursWithinRange(start, endExclusive, planStartAt);
        if (inferred.compareTo(BigDecimal.ZERO) > 0) {
            return inferred;
        }
        BigDecimal lookbackInferred = inferShiftHoursByLookback(start, 31, planStartAt);
        if (lookbackInferred.compareTo(BigDecimal.ZERO) > 0) {
            return lookbackInferred;
        }
        return DEFAULT_SHIFT_HOURS;
    }

    private BigDecimal inferShiftHoursWithinRange(LocalDate start, LocalDate endExclusive, LocalDateTime planStartAt) {
        BigDecimal maxShiftHours = BigDecimal.ZERO;
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            List<ShiftSchedule> schedules = getSchedulesImpactingDay(cursor);
            BigDecimal dayHours = calcDailyShiftHours(cursor, schedules, planStartAt);
            if (dayHours.compareTo(maxShiftHours) > 0) {
                maxShiftHours = dayHours;
            }
            cursor = cursor.plusDays(1);
        }
        return maxShiftHours;
    }

    private BigDecimal inferShiftHoursByLookback(LocalDate start, int days, LocalDateTime planStartAt) {
        BigDecimal maxShiftHours = BigDecimal.ZERO;
        for (int i = 1; i <= days; i++) {
            LocalDate day = start.minusDays(i);
            List<ShiftSchedule> schedules = getSchedulesImpactingDay(day);
            BigDecimal dayHours = calcDailyShiftHours(day, schedules, planStartAt);
            if (dayHours.compareTo(maxShiftHours) > 0) {
                maxShiftHours = dayHours;
            }
        }
        return maxShiftHours;
    }

    private List<ShiftSchedule> getSchedulesImpactingDay(LocalDate day) {
        List<ShiftSchedule> merged = new ArrayList<>();
        merged.addAll(shiftCalendarService.getSchedulesByDate(day.toString()));
        merged.addAll(shiftCalendarService.getSchedulesByDate(day.minusDays(1).toString()));
        return merged;
    }

    private BigDecimal calcDailyShiftHours(LocalDate day, List<ShiftSchedule> schedules, LocalDateTime planStartAt) {
        if (schedules == null || schedules.isEmpty()) {
            return BigDecimal.ZERO;
        }
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        List<TimeRange> ranges = new ArrayList<>();
        for (ShiftSchedule schedule : schedules) {
            TimeRange range = toEffectiveRange(schedule, dayStart, dayEnd, planStartAt);
            if (range != null) {
                ranges.add(range);
            }
        }
        if (ranges.isEmpty()) {
            return BigDecimal.ZERO;
        }
        ranges.sort(Comparator.comparing(TimeRange::start));
        long mergedMinutes = 0;
        LocalDateTime mergedStart = ranges.get(0).start;
        LocalDateTime mergedEnd = ranges.get(0).end;
        for (int i = 1; i < ranges.size(); i++) {
            TimeRange current = ranges.get(i);
            if (!current.start.isAfter(mergedEnd)) {
                if (current.end.isAfter(mergedEnd)) {
                    mergedEnd = current.end;
                }
                continue;
            }
            mergedMinutes += ChronoUnit.MINUTES.between(mergedStart, mergedEnd);
            mergedStart = current.start;
            mergedEnd = current.end;
        }
        mergedMinutes += ChronoUnit.MINUTES.between(mergedStart, mergedEnd);
        if (mergedMinutes <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mergedMinutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
    }

    private TimeRange toEffectiveRange(ShiftSchedule schedule,
                                       LocalDateTime dayStart,
                                       LocalDateTime dayEnd,
                                       LocalDateTime planStartAt) {
        if (schedule.getStartDateTime() == null || schedule.getEndDateTime() == null) {
            return null;
        }
        LocalDateTime scheduleStart = toLocalDateTime(schedule.getStartDateTime());
        LocalDateTime scheduleEnd = toLocalDateTime(schedule.getEndDateTime());
        if (planStartAt != null && scheduleStart.toLocalDate().equals(planStartAt.toLocalDate()) && scheduleEnd.isAfter(planStartAt)) {
            scheduleStart = scheduleStart.isBefore(planStartAt) ? planStartAt : scheduleStart;
        }
        LocalDate startDate = scheduleStart.toLocalDate();
        LocalDate endDate = scheduleEnd.toLocalDate();
        if (!scheduleEnd.isAfter(scheduleStart) && endDate.isAfter(startDate)) {
            scheduleEnd = scheduleStart.plusHours(24);
        }
        LocalDateTime effectiveStart = scheduleStart.isBefore(dayStart) ? dayStart : scheduleStart;
        LocalDateTime effectiveEnd = scheduleEnd.isAfter(dayEnd) ? dayEnd : scheduleEnd;
        if (!effectiveEnd.isAfter(effectiveStart)) {
            return null;
        }
        return new TimeRange(effectiveStart, effectiveEnd);
    }

    private Map<String, List<LineCapacity>> buildModelCapacities(Set<Long> scopedLineIds,
                                                                                                 List<ProductionLineModelConfig> configs,
                                                                                                 List<ProductionLine> lines) {
        Map<Long, ProductionLine> lineById = lines.stream()
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(ProductionLine::getId, line -> line, (a, b) -> a));

        Map<String, List<LineCapacity>> map = new HashMap<>();
        for (ProductionLineModelConfig cfg : configs) {
            if (cfg.getStatus() != null && cfg.getStatus() == 0) {
                continue;
            }
            if (cfg.getModel() == null || cfg.getCapacityPerHour() == null || cfg.getLineId() == null) {
                continue;
            }
            String configModel = cfg.getModel().trim();
            if (configModel.isEmpty()) {
                continue;
            }
            ProductionLine productionLine = lineById.get(cfg.getLineId());
            if (!isStandbyLine(productionLine)) {
                continue;
            }
            if (!scopedLineIds.isEmpty() && !scopedLineIds.contains(cfg.getLineId())) {
                continue;
            }
            String lineName = productionLine == null ? "产线" + cfg.getLineId() : productionLine.getLineName();
            String craft = productionLine == null ? null : productionLine.getCraft();
            map.computeIfAbsent(configModel, k -> new ArrayList<>())
                    .add(LineCapacity.of(cfg.getLineId(), lineName, configModel,
                            cfg.getCapacityPerHour(), cfg.getPriority(), craft));
        }
        map.values().forEach(this::sortLineCapacities);
        return map;
    }

    private Map<LineDayKey, Integer> buildRemainingCapacityByLineDay(LocalDate start,
                                                                      LocalDate endExclusive,
                                                                      Map<LocalDate, BigDecimal> shiftHoursByDay,
                                                                      Map<String, List<LineCapacity>> lineCapByModel,
                                                                      Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId) {
        Map<Long, LineCapacity> lineCapacityById = new HashMap<>();
        for (List<LineCapacity> capacities : lineCapByModel.values()) {
            for (LineCapacity capacity : capacities) {
                lineCapacityById.merge(capacity.lineId, capacity, this::pickHigherCapacityLine);
            }
        }

        Map<LineDayKey, Integer> remainingCapacityByLineDay = new HashMap<>();
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            BigDecimal shiftHours = shiftHoursByDay.getOrDefault(cursor, DEFAULT_SHIFT_HOURS);
            for (LineCapacity lineCapacity : lineCapacityById.values()) {
                BigDecimal effectiveCapacityPerHour = resolveEffectiveCapacityPerHour(lineCapacity, runtimeViewByLineId.get(lineCapacity.lineId));
                int dayCapacity = effectiveCapacityPerHour.multiply(nonNegative(shiftHours))
                        .setScale(0, RoundingMode.FLOOR)
                        .intValue();
                if (isLineStopped(runtimeViewByLineId.get(lineCapacity.lineId))) {
                    dayCapacity = 0;
                }
                remainingCapacityByLineDay.put(new LineDayKey(lineCapacity.lineId, cursor), Math.max(dayCapacity, 0));
            }
            cursor = cursor.plusDays(1);
        }
        return remainingCapacityByLineDay;
    }

    private Map<Long, PlanningSnapshot.LineRuntimeView> buildRuntimeViewByLineId(Set<Long> scopedLineIds) {
        if (productionLineRuntimeService == null) {
            return Collections.emptyMap();
        }
        List<ProductionLineRuntime> runtimeList = productionLineRuntimeService.list(null);
        if (runtimeList == null || runtimeList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId = new HashMap<>();
        for (ProductionLineRuntime runtime : runtimeList) {
            if (runtime == null || runtime.getLineId() == null) {
                continue;
            }
            if (!scopedLineIds.isEmpty() && !scopedLineIds.contains(runtime.getLineId())) {
                continue;
            }
            if (runtimeViewByLineId.containsKey(runtime.getLineId())) {
                continue;
            }
            LocalDateTime changeoverStartTime = toLocalDateTime(runtime.getChangeoverStartTime());
            LocalDateTime changeoverEndTime = toLocalDateTime(runtime.getChangeoverEndTime());
            if (changeoverStartTime != null && changeoverEndTime != null && changeoverEndTime.isBefore(changeoverStartTime)) {
                changeoverStartTime = null;
                changeoverEndTime = null;
            }
            runtimeViewByLineId.put(runtime.getLineId(),
                    PlanningSnapshot.LineRuntimeView.fromRuntime(runtime, normalizeRuntimeStatus(runtime.getStatus()),
                            changeoverStartTime, changeoverEndTime));
        }
        return runtimeViewByLineId;
    }

    private BigDecimal resolveEffectiveCapacityPerHour(LineCapacity lineCapacity, PlanningSnapshot.LineRuntimeView runtimeView) {
        BigDecimal fallbackCapacity = nonNegative(lineCapacity.capacityPerHour);
        if (runtimeView == null || runtimeView.getStatus() == null || runtimeView.getStatus() != RuntimeStatus.RUNNING) {
            return fallbackCapacity;
        }
        BigDecimal runtimeCapacity = nonNegative(runtimeView.getCurrentCapacity());
        if (runtimeCapacity.compareTo(BigDecimal.ZERO) > 0) {
            return runtimeCapacity;
        }
        return fallbackCapacity;
    }

    private boolean isLineStopped(PlanningSnapshot.LineRuntimeView runtimeView) {
        if (runtimeView == null || runtimeView.getStatus() == null) {
            return false;
        }
        return runtimeView.getStatus() != RuntimeStatus.IDLE
                && runtimeView.getStatus() != RuntimeStatus.RUNNING;
    }

    private boolean isStandbyLine(ProductionLine productionLine) {
        return productionLine != null && productionLine.getStatus() != null && productionLine.getStatus() == 0;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private Integer normalizeRuntimeStatus(Integer status) {
        return status;
    }

    private LineCapacity pickHigherCapacityLine(LineCapacity left,
                                                                              LineCapacity right) {
        int compare = left.capacityPerHour.compareTo(right.capacityPerHour);
        if (compare > 0) {
            return left;
        }
        if (compare < 0) {
            return right;
        }
        return compareLinePriority(left, right) <= 0 ? left : right;
    }

    private int compareLinePriority(LineCapacity left, LineCapacity right) {
        int leftPriority = left.priority != null ? left.priority : Integer.MAX_VALUE;
        int rightPriority = right.priority != null ? right.priority : Integer.MAX_VALUE;
        return Integer.compare(leftPriority, rightPriority);
    }

    private void sortLineCapacities(List<LineCapacity> lineCapacities) {
        lineCapacities.sort((left, right) -> {
            int priorityCompare = compareLinePriority(left, right);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            int capCompare = right.capacityPerHour.compareTo(left.capacityPerHour);
            if (capCompare != 0) {
                return capCompare;
            }
            return Long.compare(left.lineId, right.lineId);
        });
    }

    private Map<String, Integer> queryCurrentInventoryByKey(LocalDate inventoryCutoffDate,
                                                            Map<String, List<ProductionOrder>> orderByKey) {
        if (inventoryCutoffDate == null || orderByKey.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("cutoffDate", java.sql.Date.valueOf(inventoryCutoffDate));
        List<com.depository_manage.entity.BearingRecord> inventoryRecords = bearingRecordService.selectInventoryByCutoffDate(params);
        if (inventoryRecords == null || inventoryRecords.isEmpty()) {
            return new HashMap<>();
        }
        return inventoryRecords.stream()
                .filter(r -> r.getCustomer() != null && r.getOuterInnerRing() != null && r.getModel() != null)
                .collect(Collectors.groupingBy(this::toKey,
                        Collectors.summingInt(r -> Math.max(0, Optional.ofNullable(r.getQuantity()).orElse(0)))));
    }

    private String toKey(ProductionOrder o) {
        return ProductionPlanServiceImpl.buildNormalizedOrderKey(o.getCustomer(), o.getOuterInnerRing(), o.getModel());
    }

    private String toKey(SafetyStock s) {
        return ProductionPlanServiceImpl.buildNormalizedOrderKey(s.getCustomer(), s.getOuterInnerRing(), s.getModel());
    }

    private String toKey(com.depository_manage.entity.BearingRecord record) {
        return ProductionPlanServiceImpl.buildNormalizedOrderKey(record.getCustomer(), record.getOuterInnerRing(), record.getModel());
    }

    private LocalDateTime toLocalDateTime(Date dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toInstant().atZone(zoneId).toLocalDateTime();
    }

    private static class TimeRange {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        private LocalDateTime start() {
            return start;
        }
    }
}
