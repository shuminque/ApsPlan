package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ProductionOrderStatus;
import com.depository_manage.entity.aps.SafetyStock;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewDailyDTO;
import com.depository_manage.pojo.shift.PlanPreviewOrderDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.ProductionPlanningService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductionPlanningServiceImpl implements ProductionPlanningService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final BigDecimal DEFAULT_SHIFT_HOURS = new BigDecimal("8");
    private static final String PLAN_COLOR = "#FFB020";

    @Resource
    private ProductionOrderService productionOrderService;
    @Resource
    private SafetyStockService safetyStockService;
    @Resource
    private ShiftCalendarService shiftCalendarService;
    @Resource
    private ProductionLineModelConfigMapper modelConfigMapper;
    @Resource
    private ProductionLineMapper productionLineMapper;
    @Resource
    private BearingRecordService bearingRecordService;

    @Override
    public List<CalendarEventDTO> generatePlanCalendarEvents(String startDate, String endDate) {
        return generatePlanPreview(startDate, endDate).getEvents();
    }

    @Override
    public PlanPreviewResponseDTO generatePlanPreview(String startDate, String endDate) {
        return generatePlanPreview(startDate, endDate, "AUTO", Collections.emptyList(), "ALL", Collections.emptyList(), null);
    }

    @Override
    public PlanPreviewResponseDTO generatePlanPreview(String startDate,
                                                      String endDate,
                                                      String planMode,
                                                      List<Long> insertOrderIds,
                                                      String lineScope,
                                                      List<Long> lineIds,
                                                      Integer freezeHours) {
        LocalDateTime requestStartAt = toLocalDateTime(startDate);
        LocalDate requestStart = requestStartAt == null ? null : requestStartAt.toLocalDate();
        LocalDate endExclusive = toLocalDate(endDate);
        if (requestStart == null || endExclusive == null) {
            return new PlanPreviewResponseDTO();
        }

        LocalDate today = LocalDate.now();
        LocalDate start = requestStart.isBefore(today) ? today : requestStart;
        LocalDateTime effectiveStartAt = requestStartAt == null ? null : requestStartAt.withSecond(0).withNano(0);
        if (effectiveStartAt != null && effectiveStartAt.toLocalDate().isBefore(start)) {
            effectiveStartAt = start.atStartOfDay();
        }
        if (!start.isBefore(endExclusive)) {
            endExclusive = start.plusMonths(1);
        }
        String normalizedMode = normalizeMode(planMode);
        Set<Long> insertOrderIdSet = normalizeLongSet(insertOrderIds);
        Set<Long> scopedLineIds = normalizeLineScope(lineScope, lineIds);
        int freezeWindowHours = freezeHours == null ? 0 : Math.max(0, freezeHours);
        if (freezeWindowHours > 0 && requestStartAt != null) {
            LocalDateTime freezeEnd = requestStartAt.plusHours(freezeWindowHours);
            if (freezeEnd.isAfter(effectiveStartAt)) {
                effectiveStartAt = freezeEnd;
                start = effectiveStartAt.toLocalDate();
                if (!start.isBefore(endExclusive)) {
                    endExclusive = start.plusMonths(1);
                }
            }
        }

        List<ProductionOrder> openOrders = productionOrderService.list(new LambdaQueryWrapper<ProductionOrder>()
                .in(ProductionOrder::getStatus, ProductionOrderStatus.openStatusFilterValues())
                .gt(ProductionOrder::getQuantity, 0));
        if (openOrders.isEmpty()) {
            return new PlanPreviewResponseDTO();
        }

        List<SafetyStock> safetyStocks = safetyStockService.list();
        Map<String, SafetyStock> safetyStockByKey = safetyStocks.stream()
                .filter(s -> s.getCustomer() != null && s.getModel() != null && s.getOuterInnerRing() != null)
                .collect(Collectors.toMap(this::toKey, s -> s, (a, b) -> b));

        Map<String, List<ProductionOrder>> orderByKey = openOrders.stream()
                .filter(o -> o.getCustomer() != null && o.getModel() != null && o.getOuterInnerRing() != null)
                .collect(Collectors.groupingBy(this::toKey));

        Map<String, Integer> currentInventoryByKey = queryCurrentInventoryByKey(start, orderByKey);

        List<DemandItem> demands = buildDemands(orderByKey, safetyStockByKey, currentInventoryByKey, normalizedMode, insertOrderIdSet);
        if (demands.isEmpty()) {
            return new PlanPreviewResponseDTO();
        }

        LocalDate latestDeliveryExclusive = demands.stream()
                .map(DemandItem::earliestDeliveryDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .map(d -> d.plusDays(1))
                .orElse(endExclusive);
        if (endExclusive.isBefore(latestDeliveryExclusive)) {
            endExclusive = latestDeliveryExclusive;
        }

        Map<LocalDate, BigDecimal> shiftHoursByDay = buildShiftHours(start, endExclusive, effectiveStartAt);
        Map<String, List<LineCapacity>> lineCapByModel = buildModelCapacities(scopedLineIds);
        Map<LineDayKey, Integer> remainingCapacityByLineDay = buildRemainingCapacityByLineDay(start, endExclusive, shiftHoursByDay, lineCapByModel);
        Map<DemandItem, DemandLineMatch> barLineMatchesByDemand = buildBarLineMatchesByDemand(demands, lineCapByModel);
        List<RingPairDemand> ringPairDemands = buildRingPairDemands(demands, barLineMatchesByDemand);
        Map<String, LineActivationPlan> activationPlanByKey = new HashMap<>();

        List<PlanSlice> plannedSlices = new ArrayList<>();
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            final LocalDate day = cursor;
            schedulePairedBarDemands(day, endExclusive, ringPairDemands, remainingCapacityByLineDay, plannedSlices, activationPlanByKey);
            for (DemandItem demand : demands) {
                if (demand.remaining() <= 0) {
                    continue;
                }
                List<LineCapacity> lines = findMatchingLines(demand.model, lineCapByModel).stream()
                        .filter(line -> !line.isBarCraft())
                        .collect(Collectors.toList());
                List<LineCapacity> prioritizedLines = prioritizeCandidateLines(demand.activationKey(), lines, day, endExclusive,
                        demand.remaining(), remainingCapacityByLineDay, activationPlanByKey);
                for (LineCapacity line : prioritizedLines) {
                    if (demand.remaining() <= 0) {
                        break;
                    }
                    LineDayKey lineDayKey = new LineDayKey(line.lineId, day);
                    int remainingCapacity = remainingCapacityByLineDay.getOrDefault(lineDayKey, 0);
                    if (remainingCapacity <= 0) {
                        continue;
                    }
                    int assignQty = Math.min(remainingCapacity, demand.remaining());
                    demand.applyPlan(assignQty, day);
                    remainingCapacityByLineDay.put(lineDayKey, remainingCapacity - assignQty);
                    plannedSlices.add(new PlanSlice(day, line.lineId, line.lineName, demand.customer, demand.outerInnerRing, demand.model, assignQty));
                }
            }
            cursor = cursor.plusDays(1);
        }
        PlanPreviewResponseDTO response = new PlanPreviewResponseDTO();
        response.setEvents(mergeSlicesToEvents(plannedSlices, effectiveStartAt));
        response.setOrders(buildOrderPreviewRows(demands));
        response.setDailyOutputs(buildDailyPreviewRows(plannedSlices));
        response.setSqueezedOrderCount(calculateSqueezedOrderCount(demands));
        response.setDelayedDays(calculateDelayedDays(demands, endExclusive));
        response.setInsertFulfillmentRate(calculateInsertFulfillmentRate(demands));
        return response;
    }

    private List<DemandItem> buildDemands(Map<String, List<ProductionOrder>> orderByKey,
                                          Map<String, SafetyStock> safetyStockByKey,
                                          Map<String, Integer> currentInventoryByKey,
                                          String planMode,
                                          Set<Long> insertOrderIdSet) {
        List<DemandItem> result = new ArrayList<>();
        boolean insertMode = "INSERT".equals(planMode) && !insertOrderIdSet.isEmpty();
        for (Map.Entry<String, List<ProductionOrder>> entry : orderByKey.entrySet()) {
            List<ProductionOrder> groupOrders = entry.getValue();
            if (groupOrders.isEmpty()) {
                continue;
            }
            ProductionOrder any = groupOrders.get(0);
            int orderQty = groupOrders.stream().mapToInt(order -> {
                int qty = Optional.ofNullable(order.getQuantity()).orElse(0);
                int assigned = Optional.ofNullable(order.getAssignedQuantity()).orElse(0);
                return Math.max(0, qty - assigned);
            }).sum();
            int insertOrderQty = groupOrders.stream()
                    .filter(order -> order.getId() != null && insertOrderIdSet.contains(order.getId()))
                    .mapToInt(order -> {
                        int qty = Optional.ofNullable(order.getQuantity()).orElse(0);
                        int assigned = Optional.ofNullable(order.getAssignedQuantity()).orElse(0);
                        return Math.max(0, qty - assigned);
                    }).sum();
            int currentInventory = currentInventoryByKey.getOrDefault(entry.getKey(), 0);
            SafetyStock stock = safetyStockByKey.get(entry.getKey());
            BigDecimal safetyTarget = BigDecimal.ZERO;
            if (stock != null && stock.getStockCycle() != null && stock.getMonthlyStockQty() != null) {
                safetyTarget = stock.getStockCycle().multiply(stock.getMonthlyStockQty());
            }
            int safetyTargetQty = safetyTarget.setScale(0, RoundingMode.HALF_UP).intValue();
            int required = Math.max(0, orderQty - currentInventory + safetyTargetQty);
            if (required <= 0) {
                continue;
            }
            Date earliestDelivery = groupOrders.stream()
                    .map(ProductionOrder::getDeliveryDate)
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);
            int priority = groupOrders.stream()
                    .map(ProductionOrder::getPriority)
                    .mapToInt(this::parseOrderPriority)
                    .max()
                    .orElse(0);
            int lockedRequired = insertMode ? Math.min(required, insertOrderQty) : 0;
            int normalRequired = required - lockedRequired;
            if (lockedRequired > 0) {
                result.add(DemandItem.lockedInsert(any.getCustomer(), any.getOuterInnerRing(), any.getModel(), lockedRequired,
                        currentInventory, earliestDelivery, Math.max(priority, 2)));
            }
            if (normalRequired > 0) {
                result.add(DemandItem.normal(any.getCustomer(), any.getOuterInnerRing(), any.getModel(), normalRequired,
                        currentInventory, earliestDelivery, priority));
            }
        }

        result.sort(Comparator
                .comparing(DemandItem::lockedInsert).reversed()
                .comparingInt(DemandItem::priority).reversed()
                .thenComparingInt(DemandItem::deliveryUrgencyDays)
                .thenComparing((DemandItem d) -> d.required, Comparator.reverseOrder()));
        return result;
    }

    private int parseOrderPriority(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            return 0;
        }
        String normalized = priority.trim();
        if ("插单".equals(normalized)) {
            return 2;
        }
        if ("加急".equals(normalized)) {
            return 1;
        }
        if ("普通".equals(normalized)) {
            return 0;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    private Map<LocalDate, BigDecimal> buildShiftHours(LocalDate start, LocalDate endExclusive, LocalDateTime planStartAt) {
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            List<ShiftSchedule> schedules = shiftCalendarService.getSchedulesByDate(cursor.toString());
            BigDecimal hours = schedules.stream()
                    .map(schedule -> calcHours(schedule, planStartAt))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean isCustomStartDay = planStartAt != null
                    && cursor.equals(planStartAt.toLocalDate())
                    && !planStartAt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
            if (hours.compareTo(BigDecimal.ZERO) <= 0 && !isCustomStartDay) {
                hours = DEFAULT_SHIFT_HOURS;
            }
            result.put(cursor, hours);
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private BigDecimal calcHours(ShiftSchedule schedule, LocalDateTime planStartAt) {
        if (schedule.getStartDateTime() == null || schedule.getEndDateTime() == null) {
            return BigDecimal.ZERO;
        }
        LocalDateTime scheduleStart = schedule.getStartDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime scheduleEnd = schedule.getEndDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDate startDate = scheduleStart.toLocalDate();
        LocalDate endDate = scheduleEnd.toLocalDate();
        if (planStartAt != null && scheduleStart.toLocalDate().equals(planStartAt.toLocalDate()) && scheduleEnd.isAfter(planStartAt)) {
            scheduleStart = scheduleStart.isBefore(planStartAt) ? planStartAt : scheduleStart;
        }
        long minutes = ChronoUnit.MINUTES.between(scheduleStart, scheduleEnd);
        if (minutes <= 0 && endDate.isAfter(startDate)) {
            minutes = 24 * 60;
        }
        if (minutes <= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(minutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
    }

    private Map<String, List<LineCapacity>> buildModelCapacities(Set<Long> scopedLineIds) {
        List<ProductionLineModelConfig> configs = modelConfigMapper.selectPageList(null, null, 0L, 2000L);
        List<ProductionLine> lines = productionLineMapper.selectPageList(null, 0L, 1000L);
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
            if (productionLine != null && productionLine.getStatus() != null && productionLine.getStatus() == 0) {
                continue;
            }
            if (!scopedLineIds.isEmpty() && !scopedLineIds.contains(cfg.getLineId())) {
                continue;
            }
            String lineName = productionLine == null ? "产线" + cfg.getLineId() : productionLine.getLineName();
            String craft = productionLine == null ? null : productionLine.getCraft();
            map.computeIfAbsent(configModel, k -> new ArrayList<>())
                    .add(LineCapacity.of(cfg.getLineId(), lineName, configModel, cfg.getCapacityPerHour(), cfg.getPriority(), craft));
        }
        map.values().forEach(this::sortLineCapacities);
        return map;
    }

    private Map<LineDayKey, Integer> buildRemainingCapacityByLineDay(LocalDate start,
                                                                     LocalDate endExclusive,
                                                                     Map<LocalDate, BigDecimal> shiftHoursByDay,
                                                                     Map<String, List<LineCapacity>> lineCapByModel) {
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
                int dayCapacity = lineCapacity.capacityPerHour.multiply(shiftHours)
                        .setScale(0, RoundingMode.FLOOR)
                        .intValue();
                remainingCapacityByLineDay.put(new LineDayKey(lineCapacity.lineId, cursor), Math.max(dayCapacity, 0));
            }
            cursor = cursor.plusDays(1);
        }
        return remainingCapacityByLineDay;
    }

    private LineCapacity pickHigherCapacityLine(LineCapacity left, LineCapacity right) {
        int compare = left.capacityPerHour.compareTo(right.capacityPerHour);
        if (compare > 0) {
            return left;
        }
        if (compare < 0) {
            return right;
        }
        return compareLinePriority(left, right) <= 0 ? left : right;
    }

    List<LineCapacity> findMatchingLines(String demandModel, Map<String, List<LineCapacity>> lineCapByModel) {
        if (demandModel == null || demandModel.trim().isEmpty() || lineCapByModel == null || lineCapByModel.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedDemandModel = demandModel.trim();
        List<LineCapacity> exactMatches = lineCapByModel.get(normalizedDemandModel);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            List<LineCapacity> sortedExactMatches = new ArrayList<>(exactMatches);
            sortLineCapacities(sortedExactMatches);
            return sortedExactMatches;
        }

        List<LineCapacity> seriesMatches = new ArrayList<>();
        for (Map.Entry<String, List<LineCapacity>> entry : lineCapByModel.entrySet()) {
            String configModel = entry.getKey();
            if (!isSeriesMatch(normalizedDemandModel, configModel)) {
                continue;
            }
            seriesMatches.addAll(entry.getValue());
        }
        sortLineCapacities(seriesMatches);
        return seriesMatches;
    }

    private List<LineCapacity> prioritizeCandidateLines(String activationKey,
                                                        List<LineCapacity> lines,
                                                        LocalDate day,
                                                        LocalDate endExclusive,
                                                        int demandRemaining,
                                                        Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                                        Map<String, LineActivationPlan> activationPlanByKey) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, LineCapacity> uniqueLines = new HashMap<>();
        for (LineCapacity line : lines) {
            uniqueLines.merge(line.lineId, line, (existing, candidate) -> compareLinePriority(existing, candidate) <= 0 ? existing : candidate);
        }

        List<LineCapacity> candidates = uniqueLines.values().stream()
                .sorted((left, right) -> compareByRemainingHorizonCapacity(left, right, day, endExclusive, remainingCapacityByLineDay))
                .collect(Collectors.toList());

        LineActivationPlan activationPlan = activationPlanByKey.computeIfAbsent(activationKey, key -> new LineActivationPlan());
        activationPlan.ensureMinimumLines(candidates, day, endExclusive, demandRemaining, remainingCapacityByLineDay);

        List<LineCapacity> availableLines = activationPlan.activatedLines(candidates, day, endExclusive, demandRemaining, remainingCapacityByLineDay);
        if (!availableLines.isEmpty()) {
            return availableLines;
        }

        List<LineCapacity> fallbackLines = candidates.stream()
                .filter(line -> remainingCapacityByLineDay.getOrDefault(new LineDayKey(line.lineId, day), 0) > 0)
                .collect(Collectors.toList());
        if (fallbackLines.isEmpty()) {
            return Collections.emptyList();
        }
        activationPlan.ensureMinimumLines(fallbackLines, day, endExclusive, demandRemaining, remainingCapacityByLineDay);
        return activationPlan.activatedLines(fallbackLines, day, endExclusive, demandRemaining, remainingCapacityByLineDay);
    }

    private int compareByRemainingHorizonCapacity(LineCapacity left,
                                                  LineCapacity right,
                                                  LocalDate startDay,
                                                  LocalDate endExclusive,
                                                  Map<LineDayKey, Integer> remainingCapacityByLineDay) {
        int capacityCompare = Integer.compare(
                totalRemainingCapacity(right, startDay, endExclusive, remainingCapacityByLineDay),
                totalRemainingCapacity(left, startDay, endExclusive, remainingCapacityByLineDay)
        );
        if (capacityCompare != 0) {
            return capacityCompare;
        }
        return compareLinePriority(left, right);
    }

    private int totalRemainingCapacity(LineCapacity line,
                                       LocalDate startDay,
                                       LocalDate endExclusive,
                                       Map<LineDayKey, Integer> remainingCapacityByLineDay) {
        int total = 0;
        LocalDate cursor = startDay;
        while (cursor.isBefore(endExclusive)) {
            total += remainingCapacityByLineDay.getOrDefault(new LineDayKey(line.lineId, cursor), 0);
            cursor = cursor.plusDays(1);
        }
        return total;
    }

    private boolean isSeriesMatch(String demandModel, String configModel) {
        if (configModel == null || configModel.trim().isEmpty()) {
            return false;
        }
        String normalizedConfigModel = configModel.trim();
        return demandModel.startsWith(normalizedConfigModel);
    }

    private void sortLineCapacities(List<LineCapacity> lineCapacities) {
        lineCapacities.sort(this::compareLinePriority);
    }

    private int compareLinePriority(LineCapacity left, LineCapacity right) {
        return Comparator
                .comparing((LineCapacity l) -> l.priority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(l -> l.lineId)
                .compare(left, right);
    }

    private List<CalendarEventDTO> mergeSlicesToEvents(List<PlanSlice> slices, LocalDateTime planStartAt) {
        List<CalendarEventDTO> result = new ArrayList<>();
        if (slices.isEmpty()) {
            return result;
        }

        slices.sort(Comparator
                .comparing(PlanSlice::mergeKey)
                .thenComparing(s -> s.day));

        PlanSlice current = slices.get(0);
        LocalDate blockStart = current.day;
        LocalDate blockEnd = current.day;
        int blockQty = current.quantity;

        for (int i = 1; i < slices.size(); i++) {
            PlanSlice next = slices.get(i);
            boolean sameBlock = current.mergeKey().equals(next.mergeKey())
                    && blockEnd.plusDays(1).equals(next.day);
            if (sameBlock) {
                blockEnd = next.day;
                blockQty += next.quantity;
                continue;
            }
            result.add(buildEvent(blockStart, blockEnd.plusDays(1), current, blockQty, planStartAt));
            current = next;
            blockStart = next.day;
            blockEnd = next.day;
            blockQty = next.quantity;
        }
        result.add(buildEvent(blockStart, blockEnd.plusDays(1), current, blockQty, planStartAt));
        return result;
    }

    private List<PlanPreviewOrderDTO> buildOrderPreviewRows(List<DemandItem> demands) {
        List<PlanPreviewOrderDTO> rows = new ArrayList<>();
        for (DemandItem item : demands) {
            PlanPreviewOrderDTO row = new PlanPreviewOrderDTO();
            row.setCustomer(item.customer);
            row.setOuterInnerRing(item.outerInnerRing);
            row.setModel(item.model);
            row.setPriority(item.priority);
            LocalDate earliest = item.earliestDeliveryDate();
            row.setEarliestDeliveryDate(earliest == null ? "" : earliest.toString());
            row.setCurrentInventory(item.currentInventory);
            row.setRequiredQuantity(item.required);
            row.setPlannedQuantity(item.plannedQuantity());
            row.setPlannedDays(item.plannedDays.size());
            rows.add(row);
        }
        return rows;
    }

    private List<PlanPreviewDailyDTO> buildDailyPreviewRows(List<PlanSlice> slices) {
        List<PlanPreviewDailyDTO> rows = new ArrayList<>();
        slices.sort(Comparator.comparing((PlanSlice s) -> s.day)
                .thenComparing(s -> s.customer)
                .thenComparing(s -> s.outerInnerRing)
                .thenComparing(s -> s.model)
                .thenComparing(s -> s.lineName));
        for (PlanSlice slice : slices) {
            PlanPreviewDailyDTO row = new PlanPreviewDailyDTO();
            row.setDay(slice.day.toString());
            row.setLineName(slice.lineName);
            row.setCustomer(slice.customer);
            row.setOuterInnerRing(slice.outerInnerRing);
            row.setModel(slice.model);
            row.setQuantity(slice.quantity);
            rows.add(row);
        }
        return rows;
    }

    private CalendarEventDTO buildEvent(LocalDate startInclusive, LocalDate endExclusive, PlanSlice slice, int quantity, LocalDateTime planStartAt) {
        CalendarEventDTO dto = new CalendarEventDTO();
        dto.setTitle(String.format("[排产] %s %s/%s %s x %,d", slice.lineName, slice.customer, slice.outerInnerRing, slice.model, quantity));
        LocalDateTime eventStart = startInclusive.atStartOfDay();
        if (planStartAt != null && startInclusive.equals(planStartAt.toLocalDate())) {
            eventStart = planStartAt;
        }
        dto.setStart(eventStart.format(DATE_TIME_FMT));
        dto.setEnd(endExclusive.atStartOfDay().format(DATE_TIME_FMT));
        dto.setColor(PLAN_COLOR);
        dto.setEventType("PLAN");
        dto.setLineId(slice.lineId);
        dto.setSource("RULE_PRIORITY");
        dto.setLineName(slice.lineName);
        dto.setCustomer(slice.customer);
        dto.setOuterInnerRing(slice.outerInnerRing);
        dto.setModel(slice.model);
        dto.setQuantity(quantity);
        return dto;
    }

    private LocalDateTime toLocalDateTime(String dateTime) {
        if (dateTime == null || dateTime.trim().isEmpty()) {
            return null;
        }
        String normalized = dateTime.trim();
        if (normalized.length() == 10) {
            return LocalDate.parse(normalized).atStartOfDay();
        }
        if (normalized.length() == 16) {
            return LocalDateTime.parse(normalized + ":00", DATE_TIME_FMT);
        }
        if (normalized.length() >= 19) {
            return LocalDateTime.parse(normalized.substring(0, 19), DATE_TIME_FMT);
        }
        return LocalDate.parse(normalized.substring(0, 10)).atStartOfDay();
    }

    private LocalDate toLocalDate(String dateTime) {
        if (dateTime == null || dateTime.length() < 10) {
            return null;
        }
        return LocalDate.parse(dateTime.substring(0, 10));
    }

    private String toKey(ProductionOrder o) {
        return ProductionPlanServiceImpl.buildNormalizedOrderKey(o.getCustomer(), o.getOuterInnerRing(), o.getModel());
    }

    /**
     * 口径说明：排产预览中的在库数量统一按“排产开始日(start)截止（含当日）累计净在库”计算。
     * 该口径同时用于需求净额(required)和前端“在库数量”展示，避免前后端口径不一致。
     */
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
                .collect(Collectors.groupingBy(this::toKey, Collectors.summingInt(r -> Math.max(0, Optional.ofNullable(r.getQuantity()).orElse(0)))));
    }

    private String toKey(SafetyStock s) {
        return ProductionPlanServiceImpl.buildNormalizedOrderKey(s.getCustomer(), s.getOuterInnerRing(), s.getModel());
    }

    private String toKey(com.depository_manage.entity.BearingRecord record) {
        return ProductionPlanServiceImpl.buildNormalizedOrderKey(record.getCustomer(), record.getOuterInnerRing(), record.getModel());
    }

    private Map<DemandItem, DemandLineMatch> buildBarLineMatchesByDemand(List<DemandItem> demands,
                                                                         Map<String, List<LineCapacity>> lineCapByModel) {
        Map<DemandItem, DemandLineMatch> result = new HashMap<>();
        for (DemandItem demand : demands) {
            Map<String, List<LineCapacity>> barLinesBySeries = new HashMap<>();
            for (Map.Entry<String, List<LineCapacity>> entry : lineCapByModel.entrySet()) {
                if (!isSeriesMatch(demand.model, entry.getKey())) {
                    continue;
                }
                List<LineCapacity> barLines = entry.getValue().stream()
                        .filter(LineCapacity::isBarCraft)
                        .collect(Collectors.toList());
                if (barLines.isEmpty()) {
                    continue;
                }
                sortLineCapacities(barLines);
                barLinesBySeries.put(entry.getKey(), barLines);
            }
            result.put(demand, new DemandLineMatch(barLinesBySeries));
        }
        return result;
    }

    private List<RingPairDemand> buildRingPairDemands(List<DemandItem> demands,
                                                      Map<DemandItem, DemandLineMatch> barLineMatchesByDemand) {
        Map<String, List<DemandItem>> laDemandByCustomer = demands.stream()
                .filter(demand -> "LA".equalsIgnoreCase(demand.outerInnerRing))
                .collect(Collectors.groupingBy(DemandItem::normalizedCustomer));
        Map<String, List<DemandItem>> lbDemandByCustomer = demands.stream()
                .filter(demand -> "LB".equalsIgnoreCase(demand.outerInnerRing))
                .collect(Collectors.groupingBy(DemandItem::normalizedCustomer));
        List<RingPairDemand> pairDemands = new ArrayList<>();
        for (Map.Entry<String, List<DemandItem>> entry : laDemandByCustomer.entrySet()) {
            List<DemandItem> laDemands = entry.getValue();
            List<DemandItem> lbDemands = lbDemandByCustomer.getOrDefault(entry.getKey(), Collections.emptyList());
            if (lbDemands.isEmpty()) {
                continue;
            }
            Set<DemandItem> matchedLbDemands = new HashSet<>();
            for (DemandItem laDemand : laDemands) {
                PairCandidate bestCandidate = null;
                for (DemandItem lbDemand : lbDemands) {
                    if (matchedLbDemands.contains(lbDemand)) {
                        continue;
                    }
                    DemandLineMatch laMatch = barLineMatchesByDemand.get(laDemand);
                    DemandLineMatch lbMatch = barLineMatchesByDemand.get(lbDemand);
                    String sharedSeries = findBestSharedSeries(laMatch, lbMatch);
                    if (sharedSeries == null) {
                        continue;
                    }
                    List<LineCapacity> sharedBarLines = findSharedBarLines(laMatch, lbMatch, sharedSeries);
                    if (sharedBarLines.isEmpty()) {
                        continue;
                    }
                    PairCandidate candidate = new PairCandidate(lbDemand, sharedSeries, sharedBarLines);
                    if (bestCandidate == null || comparePairCandidate(candidate, bestCandidate) < 0) {
                        bestCandidate = candidate;
                    }
                }
                if (bestCandidate == null) {
                    continue;
                }
                matchedLbDemands.add(bestCandidate.lbDemand);
                pairDemands.add(new RingPairDemand(laDemand, bestCandidate.lbDemand, bestCandidate.sharedSeries, bestCandidate.sharedBarLines));
            }
        }

        pairDemands.sort(Comparator
                .comparing(RingPairDemand::lockedInsert).reversed()
                .comparingInt(RingPairDemand::priority).reversed()
                .thenComparingInt(RingPairDemand::deliveryUrgencyDays)
                .thenComparing(RingPairDemand::maxRequired, Comparator.reverseOrder()));
        return pairDemands;
    }

    private String normalizeMode(String planMode) {
        if (planMode == null || planMode.trim().isEmpty()) {
            return "AUTO";
        }
        String normalized = planMode.trim().toUpperCase();
        return "INSERT".equals(normalized) ? "INSERT" : "AUTO";
    }

    private Set<Long> normalizeLongSet(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Set<Long> normalizeLineScope(String lineScope, List<Long> lineIds) {
        if (!"PARTIAL".equalsIgnoreCase(lineScope)) {
            return Collections.emptySet();
        }
        return normalizeLongSet(lineIds);
    }

    private int calculateSqueezedOrderCount(List<DemandItem> demands) {
        return (int) demands.stream()
                .filter(demand -> !demand.lockedInsert())
                .filter(demand -> demand.plannedQuantity() < demand.required)
                .count();
    }

    private int calculateDelayedDays(List<DemandItem> demands, LocalDate endExclusive) {
        int delayedDays = 0;
        for (DemandItem demand : demands) {
            LocalDate dueDate = demand.earliestDeliveryDate();
            if (dueDate == null) {
                continue;
            }
            LocalDate completionDate = demand.lastPlannedDate().orElse(endExclusive.minusDays(1));
            if (completionDate.isAfter(dueDate)) {
                delayedDays += (int) ChronoUnit.DAYS.between(dueDate, completionDate);
            }
        }
        return Math.max(delayedDays, 0);
    }

    private BigDecimal calculateInsertFulfillmentRate(List<DemandItem> demands) {
        int required = demands.stream()
                .filter(DemandItem::lockedInsert)
                .mapToInt(demand -> demand.required)
                .sum();
        if (required <= 0) {
            return BigDecimal.ONE;
        }
        int planned = demands.stream()
                .filter(DemandItem::lockedInsert)
                .mapToInt(DemandItem::plannedQuantity)
                .sum();
        return BigDecimal.valueOf(planned)
                .divide(BigDecimal.valueOf(required), 4, RoundingMode.HALF_UP);
    }

    private int comparePairCandidate(PairCandidate left, PairCandidate right) {
        int sharedSeriesCompare = Integer.compare(right.sharedSeries.length(), left.sharedSeries.length());
        if (sharedSeriesCompare != 0) {
            return sharedSeriesCompare;
        }
        int sharedLineCountCompare = Integer.compare(right.sharedBarLines.size(), left.sharedBarLines.size());
        if (sharedLineCountCompare != 0) {
            return sharedLineCountCompare;
        }
        int deliveryCompare = Integer.compare(left.lbDemand.deliveryUrgencyDays(), right.lbDemand.deliveryUrgencyDays());
        if (deliveryCompare != 0) {
            return deliveryCompare;
        }
        int requiredCompare = Integer.compare(right.lbDemand.required, left.lbDemand.required);
        if (requiredCompare != 0) {
            return requiredCompare;
        }
        return compareLinePriority(left.sharedBarLines.get(0), right.sharedBarLines.get(0));
    }

    private String findBestSharedSeries(DemandLineMatch left, DemandLineMatch right) {
        if (left == null || right == null) {
            return null;
        }
        return left.seriesKeys().stream()
                .filter(right.seriesKeys()::contains)
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .orElse(null);
    }

    private List<LineCapacity> findSharedBarLines(DemandLineMatch left, DemandLineMatch right, String sharedSeries) {
        if (left == null || right == null || sharedSeries == null) {
            return Collections.emptyList();
        }
        List<LineCapacity> leftLines = left.barLinesBySeries(sharedSeries);
        List<LineCapacity> rightLines = right.barLinesBySeries(sharedSeries);
        if (leftLines.isEmpty() || rightLines.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, LineCapacity> rightByLineId = rightLines.stream()
                .collect(Collectors.toMap(LineCapacity::getLineId, line -> line, (existing, candidate) -> compareLinePriority(existing, candidate) <= 0 ? existing : candidate));
        List<LineCapacity> sharedLines = new ArrayList<>();
        for (LineCapacity leftLine : leftLines) {
            if (rightByLineId.containsKey(leftLine.getLineId())) {
                sharedLines.add(leftLine);
            }
        }
        sortLineCapacities(sharedLines);
        return sharedLines;
    }

    private void schedulePairedBarDemands(LocalDate day,
                                          LocalDate endExclusive,
                                          List<RingPairDemand> pairDemands,
                                          Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                          List<PlanSlice> plannedSlices,
                                          Map<String, LineActivationPlan> activationPlanByKey) {
        for (RingPairDemand pairDemand : pairDemands) {
            if (pairDemand.remaining() <= 0) {
                continue;
            }
            List<LineCapacity> prioritizedLines = prioritizeCandidateLines(pairDemand.activationKey(), pairDemand.sharedBarLines(), day,
                    endExclusive, pairDemand.remaining(), remainingCapacityByLineDay, activationPlanByKey);
            for (LineCapacity line : prioritizedLines) {
                if (pairDemand.remaining() <= 0) {
                    break;
                }
                LineDayKey lineDayKey = new LineDayKey(line.lineId, day);
                int remainingCapacity = remainingCapacityByLineDay.getOrDefault(lineDayKey, 0);
                if (remainingCapacity <= 0) {
                    continue;
                }
                int assignQty = Math.min(remainingCapacity, pairDemand.remaining());
                pairDemand.applyPlan(assignQty, day);
                remainingCapacityByLineDay.put(lineDayKey, remainingCapacity - assignQty);
                plannedSlices.add(new PlanSlice(day, line.lineId, line.lineName, pairDemand.customer(), "LA", pairDemand.laModel(), assignQty));
                plannedSlices.add(new PlanSlice(day, line.lineId, line.lineName, pairDemand.customer(), "LB", pairDemand.lbModel(), assignQty));
            }
        }
    }

    private static class DemandItem {
        private final String customer;
        private final String outerInnerRing;
        private final String model;
        private final int required;
        private final int currentInventory;
        private final int priority;
        private final boolean lockedInsert;
        private int coveredQuantity;
        private int plannedQuantity;
        private final Date earliestDelivery;
        private final Set<LocalDate> plannedDays = new HashSet<>();
        private DemandItem(String customer,
                           String outerInnerRing,
                           String model,
                           int required,
                           int currentInventory,
                           Date earliestDelivery,
                           int priority,
                           boolean lockedInsert) {
            this.customer = customer;
            this.outerInnerRing = outerInnerRing;
            this.model = model;
            this.required = required;
            this.currentInventory = currentInventory;
            this.priority = priority;
            this.lockedInsert = lockedInsert;
            this.coveredQuantity = 0;
            this.plannedQuantity = 0;
            this.earliestDelivery = earliestDelivery;
        }

        private static DemandItem lockedInsert(String customer, String outerInnerRing, String model, int required,
                                               int currentInventory, Date earliestDelivery, int priority) {
            return new DemandItem(customer, outerInnerRing, model, required, currentInventory, earliestDelivery, priority, true);
        }

        private static DemandItem normal(String customer, String outerInnerRing, String model, int required,
                                         int currentInventory, Date earliestDelivery, int priority) {
            return new DemandItem(customer, outerInnerRing, model, required, currentInventory, earliestDelivery, priority, false);
        }

        private int remaining() {
            return Math.max(0, required - coveredQuantity);
        }

        private int plannedQuantity() {
            return plannedQuantity;
        }

        private boolean lockedInsert() {
            return lockedInsert;
        }

        private int priority() {
            return priority;
        }

        private int deliveryUrgencyDays() {
            if (earliestDelivery == null) {
                return Integer.MAX_VALUE;
            }
            LocalDate d = earliestDeliveryDate();
            return (int) ChronoUnit.DAYS.between(LocalDate.now(), d);
        }

        private LocalDate earliestDeliveryDate() {
            if (earliestDelivery == null) {
                return null;
            }
            return earliestDelivery.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        private void applyPlan(int quantity, LocalDate day) {
            if (quantity <= 0) {
                return;
            }
            plannedQuantity += quantity;
            coveredQuantity = Math.min(required, coveredQuantity + quantity);
            plannedDays.add(day);
        }

        private boolean isLaOrLb() {
            return "LA".equalsIgnoreCase(outerInnerRing) || "LB".equalsIgnoreCase(outerInnerRing);
        }

        private String normalizedCustomer() {
            return normalize(customer);
        }

        private String activationKey() {
            return (lockedInsert ? "INSERT|" : "AUTO|") + normalizedCustomer() + "|" + normalize(outerInnerRing) + "|" + normalize(model);
        }

        private Optional<LocalDate> lastPlannedDate() {
            if (plannedDays.isEmpty()) {
                return Optional.empty();
            }
            return plannedDays.stream().max(LocalDate::compareTo);
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim().toUpperCase();
        }
    }

    private static class RingPairDemand {
        private final DemandItem laDemand;
        private final DemandItem lbDemand;
        private final String sharedSeries;
        private final List<LineCapacity> sharedBarLines;

        private RingPairDemand(DemandItem laDemand, DemandItem lbDemand, String sharedSeries, List<LineCapacity> sharedBarLines) {
            this.laDemand = laDemand;
            this.lbDemand = lbDemand;
            this.sharedSeries = sharedSeries;
            this.sharedBarLines = sharedBarLines;
        }

        private int remaining() {
            return Math.max(laDemand.remaining(), lbDemand.remaining());
        }

        private Integer maxRequired() {
            return Math.max(laDemand.required, lbDemand.required);
        }

        private int priority() {
            return Math.max(laDemand.priority(), lbDemand.priority());
        }

        private boolean lockedInsert() {
            return laDemand.lockedInsert() || lbDemand.lockedInsert();
        }

        private int deliveryUrgencyDays() {
            return Math.min(laDemand.deliveryUrgencyDays(), lbDemand.deliveryUrgencyDays());
        }

        private String customer() {
            return laDemand.customer;
        }

        private String laModel() {
            return laDemand.model;
        }

        private String lbModel() {
            return lbDemand.model;
        }

        private List<LineCapacity> sharedBarLines() {
            return sharedBarLines;
        }

        private String activationKey() {
            return laDemand.normalizedCustomer() + "|PAIR|" + normalize(sharedSeries) + "|" + normalize(laDemand.model) + "|" + normalize(lbDemand.model);
        }

        private void applyPlan(int quantity, LocalDate day) {
            laDemand.applyPlan(quantity, day);
            lbDemand.applyPlan(quantity, day);
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim().toUpperCase();
        }
    }

    private static class DemandLineMatch {
        private final Map<String, List<LineCapacity>> barLinesBySeries;

        private DemandLineMatch(Map<String, List<LineCapacity>> barLinesBySeries) {
            this.barLinesBySeries = barLinesBySeries;
        }

        private Set<String> seriesKeys() {
            return barLinesBySeries.keySet();
        }

        private List<LineCapacity> barLinesBySeries(String series) {
            return barLinesBySeries.getOrDefault(series, Collections.emptyList());
        }
    }

    private static class PairCandidate {
        private final DemandItem lbDemand;
        private final String sharedSeries;
        private final List<LineCapacity> sharedBarLines;

        private PairCandidate(DemandItem lbDemand, String sharedSeries, List<LineCapacity> sharedBarLines) {
            this.lbDemand = lbDemand;
            this.sharedSeries = sharedSeries;
            this.sharedBarLines = sharedBarLines;
        }
    }

    private static class PlanSlice {
        private final LocalDate day;
        private final Long lineId;
        private final String lineName;
        private final String customer;
        private final String outerInnerRing;
        private final String model;
        private final int quantity;

        private PlanSlice(LocalDate day, Long lineId, String lineName, String customer, String outerInnerRing, String model, int quantity) {
            this.day = day;
            this.lineId = lineId;
            this.lineName = lineName;
            this.customer = customer;
            this.outerInnerRing = outerInnerRing;
            this.model = model;
            this.quantity = quantity;
        }

        private String mergeKey() {
            return lineName + "|" + customer + "|" + outerInnerRing + "|" + model;
        }
    }

    static class LineCapacity {
        private final Long lineId;
        private final String lineName;
        private final String model;
        private final BigDecimal capacityPerHour;
        private final Integer priority;
        private final String craft;

        private LineCapacity(Long lineId, String lineName, String model, BigDecimal capacityPerHour, Integer priority, String craft) {
            this.lineId = lineId;
            this.lineName = lineName;
            this.model = model;
            this.capacityPerHour = capacityPerHour;
            this.priority = priority;
            this.craft = craft;
        }

        static LineCapacity of(Long lineId, String lineName, String model, BigDecimal capacityPerHour, Integer priority, String craft) {
            return new LineCapacity(lineId, lineName, model, capacityPerHour, priority, craft);
        }

        Long getLineId() {
            return lineId;
        }

        boolean isBarCraft() {
            return craft != null && craft.contains("棒材");
        }
    }

    private class LineActivationPlan {
        private final Set<Long> activatedLineIds = new HashSet<>();

        private void ensureMinimumLines(List<LineCapacity> sortedCandidates,
                                        LocalDate day,
                                        LocalDate endExclusive,
                                        int demandRemaining,
                                        Map<LineDayKey, Integer> remainingCapacityByLineDay) {
            if (sortedCandidates == null || sortedCandidates.isEmpty() || demandRemaining <= 0) {
                return;
            }
            int coveredCapacity = activatedCapacity(sortedCandidates, day, endExclusive, remainingCapacityByLineDay);
            if (coveredCapacity >= demandRemaining) {
                return;
            }
            for (LineCapacity candidate : sortedCandidates) {
                if (activatedLineIds.contains(candidate.lineId)) {
                    continue;
                }
                activatedLineIds.add(candidate.lineId);
                coveredCapacity += totalRemainingCapacity(candidate, day, endExclusive, remainingCapacityByLineDay);
                if (coveredCapacity >= demandRemaining) {
                    break;
                }
            }
        }

        private int activatedCapacity(List<LineCapacity> sortedCandidates,
                                      LocalDate day,
                                      LocalDate endExclusive,
                                      Map<LineDayKey, Integer> remainingCapacityByLineDay) {
            int total = 0;
            for (LineCapacity line : sortedCandidates) {
                if (!activatedLineIds.contains(line.lineId)) {
                    continue;
                }
                total += totalRemainingCapacity(line, day, endExclusive, remainingCapacityByLineDay);
            }
            return total;
        }

        private List<LineCapacity> activatedLines(List<LineCapacity> sortedCandidates,
                                                  LocalDate day,
                                                  LocalDate endExclusive,
                                                  int demandRemaining,
                                                  Map<LineDayKey, Integer> remainingCapacityByLineDay) {
            ensureMinimumLines(sortedCandidates, day, endExclusive, demandRemaining, remainingCapacityByLineDay);
            return sortedCandidates.stream()
                    .filter(line -> activatedLineIds.contains(line.lineId))
                    .filter(line -> remainingCapacityByLineDay.getOrDefault(new LineDayKey(line.lineId, day), 0) > 0)
                    .collect(Collectors.toList());
        }
    }

    private static class LineDayKey {
        private final Long lineId;
        private final LocalDate day;

        private LineDayKey(Long lineId, LocalDate day) {
            this.lineId = lineId;
            this.day = day;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LineDayKey)) {
                return false;
            }
            LineDayKey that = (LineDayKey) o;
            return Objects.equals(lineId, that.lineId) && Objects.equals(day, that.day);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lineId, day);
        }
    }
}
