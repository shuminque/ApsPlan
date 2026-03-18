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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        LocalDate requestStart = toLocalDate(startDate);
        LocalDate endExclusive = toLocalDate(endDate);
        if (requestStart == null || endExclusive == null) {
            return new PlanPreviewResponseDTO();
        }

        LocalDate today = LocalDate.now();
        LocalDate start = requestStart.isBefore(today) ? today : requestStart;
        if (!start.isBefore(endExclusive)) {
            endExclusive = start.plusMonths(1);
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

        List<DemandItem> demands = buildDemands(orderByKey, safetyStockByKey, currentInventoryByKey);
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

        Map<LocalDate, BigDecimal> shiftHoursByDay = buildShiftHours(start, endExclusive);
        Map<String, List<LineCapacity>> lineCapByModel = buildModelCapacities();

        List<PlanSlice> plannedSlices = new ArrayList<>();
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            final LocalDate day = cursor;
            final BigDecimal shiftHours = shiftHoursByDay.getOrDefault(day, DEFAULT_SHIFT_HOURS);
            for (DemandItem demand : demands) {
                if (demand.remaining <= 0) {
                    continue;
                }
                List<LineCapacity> lines = findMatchingLines(demand.model, lineCapByModel);
                for (LineCapacity line : lines) {
                    if (demand.remaining <= 0) {
                        break;
                    }
                    int dayCapacity = line.capacityPerHour.multiply(shiftHours)
                            .setScale(0, RoundingMode.FLOOR)
                            .intValue();
                    if (dayCapacity <= 0) {
                        continue;
                    }
                    int assignQty = Math.min(dayCapacity, demand.remaining);
                    demand.remaining -= assignQty;
                    demand.recordPlanDay(day);
                    plannedSlices.add(new PlanSlice(day, line.lineName, demand.customer, demand.outerInnerRing, demand.model, assignQty));
                }
            }
            cursor = cursor.plusDays(1);
        }
        PlanPreviewResponseDTO response = new PlanPreviewResponseDTO();
        response.setEvents(mergeSlicesToEvents(plannedSlices));
        response.setOrders(buildOrderPreviewRows(demands));
        response.setDailyOutputs(buildDailyPreviewRows(plannedSlices));
        return response;
    }

    private List<DemandItem> buildDemands(Map<String, List<ProductionOrder>> orderByKey,
                                          Map<String, SafetyStock> safetyStockByKey,
                                          Map<String, Integer> currentInventoryByKey) {
        List<DemandItem> result = new ArrayList<>();
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

            result.add(new DemandItem(any.getCustomer(), any.getOuterInnerRing(), any.getModel(), required, currentInventory, earliestDelivery));
        }

        result.sort(Comparator
                .comparingInt(DemandItem::deliveryUrgencyDays)
                .thenComparing((DemandItem d) -> d.required, Comparator.reverseOrder()));
        return result;
    }

    private Map<LocalDate, BigDecimal> buildShiftHours(LocalDate start, LocalDate endExclusive) {
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            List<ShiftSchedule> schedules = shiftCalendarService.getSchedulesByDate(cursor.toString());
            BigDecimal hours = schedules.stream()
                    .map(this::calcHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (hours.compareTo(BigDecimal.ZERO) <= 0) {
                hours = DEFAULT_SHIFT_HOURS;
            }
            result.put(cursor, hours);
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private BigDecimal calcHours(ShiftSchedule schedule) {
        if (schedule.getStartDateTime() == null || schedule.getEndDateTime() == null) {
            return BigDecimal.ZERO;
        }
        LocalDate startDate = schedule.getStartDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = schedule.getEndDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        long minutes = ChronoUnit.MINUTES.between(
                schedule.getStartDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                schedule.getEndDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        );
        if (minutes <= 0 && endDate.isAfter(startDate)) {
            minutes = 24 * 60;
        }
        if (minutes <= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(minutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
    }

    private Map<String, List<LineCapacity>> buildModelCapacities() {
        List<ProductionLineModelConfig> configs = modelConfigMapper.selectPageList(null, null, 0L, 2000L);
        List<ProductionLine> lines = productionLineMapper.selectPageList(null, 0L, 1000L);
        Map<Long, String> lineNameById = lines.stream().collect(Collectors.toMap(ProductionLine::getId, ProductionLine::getLineName, (a, b) -> a));

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
            String lineName = lineNameById.getOrDefault(cfg.getLineId(), "产线" + cfg.getLineId());
            map.computeIfAbsent(configModel, k -> new ArrayList<>())
                    .add(new LineCapacity(cfg.getLineId(), lineName, configModel, cfg.getCapacityPerHour(), cfg.getPriority()));
        }
        map.values().forEach(this::sortLineCapacities);
        return map;
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

    private boolean isSeriesMatch(String demandModel, String configModel) {
        if (configModel == null || configModel.trim().isEmpty()) {
            return false;
        }
        String normalizedConfigModel = configModel.trim();
        return demandModel.startsWith(normalizedConfigModel);
    }

    private void sortLineCapacities(List<LineCapacity> lineCapacities) {
        lineCapacities.sort(Comparator
                .comparing((LineCapacity l) -> l.priority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(l -> l.lineId));
    }

    private List<CalendarEventDTO> mergeSlicesToEvents(List<PlanSlice> slices) {
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
            result.add(buildEvent(blockStart, blockEnd.plusDays(1), current, blockQty));
            current = next;
            blockStart = next.day;
            blockEnd = next.day;
            blockQty = next.quantity;
        }
        result.add(buildEvent(blockStart, blockEnd.plusDays(1), current, blockQty));
        return result;
    }

    private List<PlanPreviewOrderDTO> buildOrderPreviewRows(List<DemandItem> demands) {
        List<PlanPreviewOrderDTO> rows = new ArrayList<>();
        for (DemandItem item : demands) {
            PlanPreviewOrderDTO row = new PlanPreviewOrderDTO();
            row.setCustomer(item.customer);
            row.setOuterInnerRing(item.outerInnerRing);
            row.setModel(item.model);
            LocalDate earliest = item.earliestDeliveryDate();
            row.setEarliestDeliveryDate(earliest == null ? "" : earliest.toString());
            row.setCurrentInventory(item.currentInventory);
            row.setRequiredQuantity(item.required);
            row.setPlannedQuantity(item.required - item.remaining);
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

    private CalendarEventDTO buildEvent(LocalDate startInclusive, LocalDate endExclusive, PlanSlice slice, int quantity) {
        CalendarEventDTO dto = new CalendarEventDTO();
        dto.setTitle(String.format("[排产] %s %s/%s %s x %,d", slice.lineName, slice.customer, slice.outerInnerRing, slice.model, quantity));
        dto.setStart(startInclusive.atStartOfDay().format(DATE_TIME_FMT));
        dto.setEnd(endExclusive.atStartOfDay().format(DATE_TIME_FMT));
        dto.setColor(PLAN_COLOR);
        dto.setEventType("PLAN");
        dto.setSource("RULE_PRIORITY");
        dto.setLineId(slice.lineId);
        dto.setLineName(slice.lineName);
        dto.setCustomer(slice.customer);
        dto.setOuterInnerRing(slice.outerInnerRing);
        dto.setModel(slice.model);
        dto.setQuantity(quantity);
        return dto;
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

    private static class DemandItem {
        private final String customer;
        private final String outerInnerRing;
        private final String model;
        private final int required;
        private final int currentInventory;
        private int remaining;
        private final Date earliestDelivery;
        private final java.util.Set<LocalDate> plannedDays = new java.util.HashSet<>();
        private DemandItem(String customer, String outerInnerRing, String model, int required, int currentInventory, Date earliestDelivery) {
            this.customer = customer;
            this.outerInnerRing = outerInnerRing;
            this.model = model;
            this.required = required;
            this.currentInventory = currentInventory;
            this.remaining = required;
            this.earliestDelivery = earliestDelivery;
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

        private void recordPlanDay(LocalDate day) {
            plannedDays.add(day);
        }
    }

    private static class PlanSlice {
        private final LocalDate day;
        private final String lineName;
        private final String customer;
        private final String outerInnerRing;
        private final String model;
        private final int quantity;
        public Long lineId;

        private PlanSlice(LocalDate day, String lineName, String customer, String outerInnerRing, String model, int quantity) {
            this.day = day;
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

        private LineCapacity(Long lineId, String lineName, String model, BigDecimal capacityPerHour, Integer priority) {
            this.lineId = lineId;
            this.lineName = lineName;
            this.model = model;
            this.capacityPerHour = capacityPerHour;
            this.priority = priority;
        }

        static LineCapacity of(Long lineId, String lineName, String model, BigDecimal capacityPerHour, Integer priority) {
            return new LineCapacity(lineId, lineName, model, capacityPerHour, priority);
        }

        Long getLineId() {
            return lineId;
        }
    }
}
