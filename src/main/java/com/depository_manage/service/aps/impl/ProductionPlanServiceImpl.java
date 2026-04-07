package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ProductionOrderStatus;
import com.depository_manage.entity.aps.ProductionPlan;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.exception.MyException;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionOrderMapper;
import com.depository_manage.mapper.aps.ProductionPlanItemMapper;
import com.depository_manage.mapper.aps.ProductionPlanMapper;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.service.aps.ProductionPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductionPlanServiceImpl implements ProductionPlanService {

    private static final Logger log = LoggerFactory.getLogger(ProductionPlanServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern PLAN_TITLE_PATTERN = Pattern.compile("^(.*?)\\s+(.*?)\\/(.*?)\\s+(.*?)\\s+x\\s+([0-9,]+)$");
    private static final long ROLLBACK_FREEZE_HOURS = 4L;

    @Resource
    private ProductionPlanMapper productionPlanMapper;
    @Resource
    private ProductionPlanItemMapper productionPlanItemMapper;
    @Resource
    private ProductionOrderMapper productionOrderMapper;
    @Resource
    private ProductionLineMapper productionLineMapper;

    @Override
    @Transactional(transactionManager = "apsTransactionManager", rollbackFor = Exception.class)
    public int commitPlan(List<CalendarEventDTO> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }

        List<ParsedPlanEvent> parsedEvents = events.stream()
                .map(this::parseEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (parsedEvents.isEmpty()) {
            return 0;
        }

        LocalDateTime rollbackFrom = parsedEvents.stream()
                .map(p -> toLocalDateTime(p.startDate))
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime rollbackTo = parsedEvents.stream()
                .map(p -> toLocalDateTime(p.endDate))
                .max(LocalDateTime::compareTo)
                .orElse(null);
        Set<Long> rollbackLineIds = parsedEvents.stream()
                .map(p -> p.lineId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        rollbackPlanWindow(rollbackFrom, rollbackTo, rollbackLineIds);

        Map<String, List<ProductionOrder>> orderGroupMap = loadOpenOrdersByKey();
        boolean hasOrderDemand = parsedEvents.stream().anyMatch(p -> p.orderDemandQty > 0);
        boolean hasMatchedOrder = !hasOrderDemand || parsedEvents.stream()
                .filter(p -> p.orderDemandQty > 0)
                .map(ParsedPlanEvent::orderKey)
                .anyMatch(orderKey -> {
                    List<ProductionOrder> candidates = orderGroupMap.get(orderKey);
                    return candidates != null && !candidates.isEmpty();
                });
        if (!hasMatchedOrder) {
            throw new MyException("未写入任何排产明细，请检查订单匹配条件或预览数据");
        }

        String batchNo = "PLAN-" + LocalDateTime.now().format(BATCH_FMT);
        Date now = new Date();

        int totalAssignQty = parsedEvents.stream().mapToInt(p -> p.assignQty).sum();
        Date minStart = parsedEvents.stream().map(p -> p.startDate).min(Date::compareTo).orElse(now);
        Date maxEnd = parsedEvents.stream().map(p -> p.endDate).max(Date::compareTo).orElse(now);

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanBatchNo(batchNo);
        plan.setSource("RULE_PRIORITY");
        plan.setStartDate(minStart);
        plan.setEndDate(maxEnd);
        plan.setTotalAssignQty(totalAssignQty);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        productionPlanMapper.insert(plan);

        int inserted = 0;
        for (ParsedPlanEvent parsed : parsedEvents) {
            int leftOrderDemandQty = parsed.orderDemandQty;
            if (leftOrderDemandQty > 0) {
                List<ProductionOrder> candidates = orderGroupMap.getOrDefault(parsed.orderKey(), new ArrayList<>());
                if (candidates.isEmpty()) {
                    log.warn("skip order demand allocation because no open order matched, key={}", parsed.orderKey());
                }
                for (ProductionOrder order : candidates) {
                    if (leftOrderDemandQty <= 0) {
                        break;
                    }
                    int assigned = Optional.ofNullable(order.getAssignedQuantity()).orElse(0);
                    int quantity = Optional.ofNullable(order.getQuantity()).orElse(0);
                    int remain = Math.max(0, quantity - assigned);
                    if (remain <= 0) {
                        continue;
                    }
                    int assignToOrder = Math.min(leftOrderDemandQty, remain);
                    validateDemandBreakdown(assignToOrder, assignToOrder, 0, "order_id=" + order.getId());

                    ProductionPlanItem item = new ProductionPlanItem();
                    item.setPlanId(plan.getId());
                    item.setPlanBatchNo(batchNo);
                    item.setOrderId(order.getId());
                    item.setCustomer(order.getCustomer());
                    item.setModel(order.getModel());
                    item.setOuterInnerRing(order.getOuterInnerRing());
                    item.setLineId(parsed.lineId);
                    item.setLineName(parsed.lineName);
                    item.setStartDate(parsed.startDate);
                    item.setEndDate(parsed.endDate);
                    item.setAssignQty(assignToOrder);
                    item.setOrderDemandQty(assignToOrder);
                    item.setSafetyDemandQty(0);
                    item.setSource("RULE_PRIORITY");
                    item.setCreatedAt(now);
                    item.setUpdatedAt(now);
                    productionPlanItemMapper.insert(item);
                    inserted++;

                    order.setAssignedQuantity(assigned + assignToOrder);
                    order.setStatus(ProductionOrderStatus.PLANNED.getCode());
                    productionOrderMapper.updateById(order);
                    leftOrderDemandQty -= assignToOrder;
                }
                if (leftOrderDemandQty > 0) {
                    log.warn("order demand not fully allocated, remaining_qty={}, key={}", leftOrderDemandQty, parsed.orderKey());
                }
            }

            if (parsed.safetyDemandQty > 0) {
                validateDemandBreakdown(parsed.safetyDemandQty, 0, parsed.safetyDemandQty,
                        "line_id=" + parsed.lineId + ",line_name=" + parsed.lineName);
                ProductionPlanItem safetyItem = new ProductionPlanItem();
                safetyItem.setPlanId(plan.getId());
                safetyItem.setPlanBatchNo(batchNo);
                safetyItem.setOrderId(null);
                safetyItem.setCustomer(parsed.customer);
                safetyItem.setModel(parsed.model);
                safetyItem.setOuterInnerRing(parsed.outerInnerRing);
                safetyItem.setLineId(parsed.lineId);
                safetyItem.setLineName(parsed.lineName);
                safetyItem.setStartDate(parsed.startDate);
                safetyItem.setEndDate(parsed.endDate);
                safetyItem.setAssignQty(parsed.safetyDemandQty);
                safetyItem.setOrderDemandQty(0);
                safetyItem.setSafetyDemandQty(parsed.safetyDemandQty);
                safetyItem.setSource("RULE_PRIORITY");
                safetyItem.setCreatedAt(now);
                safetyItem.setUpdatedAt(now);
                productionPlanItemMapper.insert(safetyItem);
                inserted++;
            }
        }

        if (inserted == 0) {
            throw new MyException("未写入任何排产明细，请检查订单匹配条件或预览数据");
        }

        return inserted;
    }

    @Override
    public int rollbackPlanWindow(LocalDateTime from, LocalDateTime to, Set<Long> lineIds) {
        if (from == null || to == null || from.isAfter(to)) {
            return 0;
        }
        if (lineIds == null || lineIds.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime freezeEnd = now.plusHours(ROLLBACK_FREEZE_HOURS);
        LocalDateTime rollbackStart = from.isAfter(freezeEnd) ? from : freezeEnd;
        if (!rollbackStart.isBefore(to)) {
            return 0;
        }

        Date rollbackStartDate = toDate(rollbackStart);
        Date rollbackEndDate = toDate(to);
        List<ProductionPlanItem> rollbackItems = productionPlanItemMapper.selectList(new LambdaQueryWrapper<ProductionPlanItem>()
                .in(ProductionPlanItem::getLineId, lineIds)
                .gt(ProductionPlanItem::getEndDate, rollbackStartDate)
                .lt(ProductionPlanItem::getStartDate, rollbackEndDate));
        if (rollbackItems.isEmpty()) {
            return 0;
        }

        Map<Long, Integer> rollbackQtyByOrder = rollbackItems.stream()
                .filter(item -> item.getOrderId() != null)
                .collect(Collectors.groupingBy(ProductionPlanItem::getOrderId, Collectors.summingInt(item -> Optional.ofNullable(item.getOrderDemandQty()).orElse(0))));
        if (!rollbackQtyByOrder.isEmpty()) {
            List<ProductionOrder> rollbackOrders = productionOrderMapper.selectBatchIds(rollbackQtyByOrder.keySet());
            Date nowDate = new Date();
            for (ProductionOrder order : rollbackOrders) {
                int assigned = Optional.ofNullable(order.getAssignedQuantity()).orElse(0);
                int rollbackQty = rollbackQtyByOrder.getOrDefault(order.getId(), 0);
                int updatedAssigned = Math.max(0, assigned - rollbackQty);
                order.setAssignedQuantity(updatedAssigned);
                refreshOrderStatus(order, updatedAssigned);
                order.setUpdatedAt(nowDate);
                productionOrderMapper.updateById(order);
            }
        }

        List<Long> itemIds = rollbackItems.stream()
                .map(ProductionPlanItem::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!itemIds.isEmpty()) {
            return productionPlanItemMapper.deleteBatchIds(itemIds);
        }
        return 0;
    }

    private Map<String, List<ProductionOrder>> loadOpenOrdersByKey() {
        List<ProductionOrder> orders = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                .in(ProductionOrder::getStatus, ProductionOrderStatus.openStatusFilterValues())
                .isNotNull(ProductionOrder::getCustomer)
                .isNotNull(ProductionOrder::getModel)
                .isNotNull(ProductionOrder::getOuterInnerRing)
                .orderByAsc(ProductionOrder::getDeliveryDate)
                .orderByAsc(ProductionOrder::getId));

        Map<String, List<ProductionOrder>> map = new HashMap<>();
        for (ProductionOrder order : orders) {
            int assigned = Optional.ofNullable(order.getAssignedQuantity()).orElse(0);
            int total = Optional.ofNullable(order.getQuantity()).orElse(0);
            if (assigned >= total) {
                continue;
            }
            map.computeIfAbsent(buildNormalizedOrderKey(order.getCustomer(), order.getOuterInnerRing(), order.getModel()), k -> new ArrayList<>())
                    .add(order);
        }
        return map;
    }

    private ParsedPlanEvent parseEvent(CalendarEventDTO event) {
        if (event == null || event.getTitle() == null || event.getStart() == null || event.getEnd() == null) {
            return null;
        }

        ParsedPlanEvent parsedFromFields = parseEventFromStructuredFields(event);
        if (parsedFromFields != null) {
            return parsedFromFields;
        }

        String title = event.getTitle().trim();
        if (title.startsWith("[排产]")) {
            title = title.substring("[排产]".length()).trim();
        }
        Matcher matcher = PLAN_TITLE_PATTERN.matcher(title);
        if (!matcher.matches()) {
            return null;
        }

        String lineName = normalizePlanKeyPart(matcher.group(1));
        String customer = normalizePlanKeyPart(matcher.group(2));
        String outerInnerRing = normalizePlanKeyPart(matcher.group(3));
        String model = normalizePlanKeyPart(matcher.group(4));
        int assignQty = Integer.parseInt(matcher.group(5).replace(",", ""));

        LocalDateTime start = LocalDateTime.parse(event.getStart(), DATE_TIME_FMT);
        LocalDateTime end = LocalDateTime.parse(event.getEnd(), DATE_TIME_FMT);

        ParsedPlanEvent parsed = new ParsedPlanEvent();
        parsed.lineName = lineName;
        parsed.lineId = resolveLineId(lineName);
        parsed.customer = customer;
        parsed.outerInnerRing = outerInnerRing;
        parsed.model = model;
        parsed.assignQty = assignQty;
        parsed.orderDemandQty = assignQty;
        parsed.safetyDemandQty = 0;
        validateDemandBreakdown(parsed.assignQty, parsed.orderDemandQty, parsed.safetyDemandQty,
                "event_title=" + event.getTitle());
        parsed.startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
        parsed.endDate = Date.from(end.atZone(ZoneId.systemDefault()).toInstant());
        return parsed;
    }

    private ParsedPlanEvent parseEventFromStructuredFields(CalendarEventDTO event) {
        String customer = normalizePlanKeyPart(event.getCustomer());
        String outerInnerRing = normalizePlanKeyPart(event.getOuterInnerRing());
        String model = normalizePlanKeyPart(event.getModel());
        String lineName = normalizePlanKeyPart(event.getLineName());
        Integer quantity = event.getQuantity();
        if (customer.isEmpty() || outerInnerRing.isEmpty() || model.isEmpty() || lineName.isEmpty() || quantity == null || quantity <= 0) {
            return null;
        }

        LocalDateTime start = LocalDateTime.parse(event.getStart(), DATE_TIME_FMT);
        LocalDateTime end = LocalDateTime.parse(event.getEnd(), DATE_TIME_FMT);

        ParsedPlanEvent parsed = new ParsedPlanEvent();
        parsed.lineName = lineName;
        parsed.lineId = event.getLineId() != null ? event.getLineId() : resolveLineId(lineName);
        parsed.customer = customer;
        parsed.outerInnerRing = outerInnerRing;
        parsed.model = model;
        parsed.assignQty = quantity;
        parsed.orderDemandQty = Optional.ofNullable(event.getOrderDemandQty()).orElse(quantity);
        parsed.safetyDemandQty = Optional.ofNullable(event.getSafetyDemandQty()).orElse(0);
        validateDemandBreakdown(parsed.assignQty, parsed.orderDemandQty, parsed.safetyDemandQty,
                "event_id=" + event.getId() + ",event_title=" + event.getTitle());
        parsed.startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
        parsed.endDate = Date.from(end.atZone(ZoneId.systemDefault()).toInstant());
        return parsed;
    }

    private void validateDemandBreakdown(int assignQty, int orderDemandQty, int safetyDemandQty, String context) {
        if (orderDemandQty < 0 || safetyDemandQty < 0) {
            log.error("invalid demand qty: order_demand_qty={}, safety_demand_qty={}, assign_qty={}, context={}",
                    orderDemandQty, safetyDemandQty, assignQty, context);
            throw new MyException("计划明细数量非法：需求字段不能为负数");
        }
        if (assignQty != orderDemandQty + safetyDemandQty) {
            log.error("demand mismatch: assign_qty={}, order_demand_qty={}, safety_demand_qty={}, context={}",
                    assignQty, orderDemandQty, safetyDemandQty, context);
            throw new MyException("计划明细数量非法：assign_qty 与需求拆分不一致");
        }
    }

    private Long resolveLineId(String lineName) {
        String normalizedLineName = normalizePlanKeyPart(lineName);
        if (normalizedLineName == null || normalizedLineName.isEmpty()) {
            return null;
        }
        List<ProductionLine> lines = productionLineMapper.selectPageList(normalizedLineName, 0L, 50L);
        if (lines.isEmpty()) {
            return null;
        }
        return lines.stream()
                .filter(line -> normalizedLineName.equals(normalizePlanKeyPart(line.getLineName())))
                .map(ProductionLine::getId)
                .findFirst()
                .orElse(lines.get(0).getId());
    }

    static String buildNormalizedOrderKey(String customer, String outerInnerRing, String model) {
        return normalizePlanKeyPart(customer) + "|" + normalizePlanKeyPart(outerInnerRing) + "|" + normalizePlanKeyPart(model);
    }

    static String normalizePlanKeyPart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private void refreshOrderStatus(ProductionOrder order, int assignedQty) {
        String normalizedStatus = ProductionOrderStatus.normalize(order.getStatus());
        if (ProductionOrderStatus.COMPLETED.getCode().equals(normalizedStatus)) {
            return;
        }
        if (assignedQty <= 0) {
            order.setStatus(ProductionOrderStatus.PENDING.getCode());
            return;
        }
        order.setStatus(ProductionOrderStatus.PLANNED.getCode());
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static class ParsedPlanEvent {
        private String customer;
        private String outerInnerRing;
        private String model;
        private Long lineId;
        private String lineName;
        private Date startDate;
        private Date endDate;
        private int assignQty;
        private int orderDemandQty;
        private int safetyDemandQty;

        private String orderKey() {
            return buildNormalizedOrderKey(customer, outerInnerRing, model);
        }
    }
}
