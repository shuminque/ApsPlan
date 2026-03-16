package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.SafetyStock;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.CalendarEventDTO;
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

    @Override
    public List<CalendarEventDTO> generatePlanCalendarEvents(String startDate, String endDate) {
        LocalDate start = toLocalDate(startDate);
        LocalDate endExclusive = toLocalDate(endDate);
        if (start == null || endExclusive == null || !start.isBefore(endExclusive)) {
            return new ArrayList<>();
        }

        List<ProductionOrder> openOrders = productionOrderService.list(new LambdaQueryWrapper<ProductionOrder>()
                .in(ProductionOrder::getStatus, "待排产", "已排产")
                .gt(ProductionOrder::getQuantity, 0));
        if (openOrders.isEmpty()) {
            return new ArrayList<>();
        }

        List<SafetyStock> safetyStocks = safetyStockService.list();
        Map<String, SafetyStock> safetyStockByKey = safetyStocks.stream()
                .filter(s -> s.getCustomer() != null && s.getModel() != null && s.getOuterInnerRing() != null)
                .collect(Collectors.toMap(this::toKey, s -> s, (a, b) -> b));

        Map<String, List<ProductionOrder>> orderByKey = openOrders.stream()
                .filter(o -> o.getCustomer() != null && o.getModel() != null && o.getOuterInnerRing() != null)
                .collect(Collectors.groupingBy(this::toKey));

        List<DemandItem> demands = buildDemands(orderByKey, safetyStockByKey);
        if (demands.isEmpty()) {
            return new ArrayList<>();
        }

        Map<LocalDate, BigDecimal> shiftHoursByDay = buildShiftHours(start, endExclusive);
        Map<String, List<LineCapacity>> lineCapByModel = buildModelCapacities();

        List<CalendarEventDTO> plannedEvents = new ArrayList<>();
        LocalDate cursor = start;
        while (cursor.isBefore(endExclusive)) {
            final LocalDate day = cursor;
            final BigDecimal shiftHours = shiftHoursByDay.getOrDefault(day, DEFAULT_SHIFT_HOURS);
            for (DemandItem demand : demands) {
                if (demand.remaining <= 0) {
                    continue;
                }
                List<LineCapacity> lines = lineCapByModel.getOrDefault(demand.model, new ArrayList<>());
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
                    plannedEvents.add(buildEvent(day, line.lineName, demand, assignQty));
                }
            }
            cursor = cursor.plusDays(1);
        }
        return plannedEvents;
    }

    private List<DemandItem> buildDemands(Map<String, List<ProductionOrder>> orderByKey, Map<String, SafetyStock> safetyStockByKey) {
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
            // 当前库存暂按 0 处理：现阶段项目未提供可用库存字段，后续可替换为库存服务查询值。
            int currentInventory = 0;
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

            result.add(new DemandItem(any.getCustomer(), any.getOuterInnerRing(), any.getModel(), required, earliestDelivery));
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
            String lineName = lineNameById.getOrDefault(cfg.getLineId(), "产线" + cfg.getLineId());
            map.computeIfAbsent(cfg.getModel(), k -> new ArrayList<>())
                    .add(new LineCapacity(cfg.getLineId(), lineName, cfg.getCapacityPerHour()));
        }
        map.values().forEach(list -> list.sort(Comparator.comparing(l -> l.lineId)));
        return map;
    }

    private CalendarEventDTO buildEvent(LocalDate day, String lineName, DemandItem demand, int quantity) {
        CalendarEventDTO dto = new CalendarEventDTO();
        dto.setTitle(String.format("[排产] %s %s/%s %s x %,d", lineName, demand.customer, demand.outerInnerRing, demand.model, quantity));
        dto.setStart(day.atStartOfDay().format(DATE_TIME_FMT));
        dto.setEnd(day.plusDays(1).atStartOfDay().format(DATE_TIME_FMT));
        dto.setColor(PLAN_COLOR);
        return dto;
    }

    private LocalDate toLocalDate(String dateTime) {
        if (dateTime == null || dateTime.length() < 10) {
            return null;
        }
        return LocalDate.parse(dateTime.substring(0, 10));
    }

    private String toKey(ProductionOrder o) {
        return o.getCustomer() + "|" + o.getOuterInnerRing() + "|" + o.getModel();
    }

    private String toKey(SafetyStock s) {
        return s.getCustomer() + "|" + s.getOuterInnerRing() + "|" + s.getModel();
    }

    private static class DemandItem {
        private final String customer;
        private final String outerInnerRing;
        private final String model;
        private final int required;
        private int remaining;
        private final Date earliestDelivery;
        private DemandItem(String customer, String outerInnerRing, String model, int required, Date earliestDelivery) {
            this.customer = customer;
            this.outerInnerRing = outerInnerRing;
            this.model = model;
            this.required = required;
            this.remaining = required;
            this.earliestDelivery = earliestDelivery;
        }

        private int deliveryUrgencyDays() {
            if (earliestDelivery == null) {
                return Integer.MAX_VALUE;
            }
            LocalDate d = earliestDelivery.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return (int) ChronoUnit.DAYS.between(LocalDate.now(), d);
        }
    }

    private static class LineCapacity {
        private final Long lineId;
        private final String lineName;
        private final BigDecimal capacityPerHour;

        private LineCapacity(Long lineId, String lineName, BigDecimal capacityPerHour) {
            this.lineId = lineId;
            this.lineName = lineName;
            this.capacityPerHour = capacityPerHour;
        }
    }
}
