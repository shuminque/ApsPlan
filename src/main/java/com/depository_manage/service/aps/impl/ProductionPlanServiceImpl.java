package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionLineRuntime;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ProductionOrderStatus;
import com.depository_manage.entity.aps.ProductionPlan;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.entity.aps.RuntimeStatus;
import com.depository_manage.exception.MyException;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.mapper.aps.ProductionLineRuntimeMapper;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
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
    @Resource
    private ProductionLineRuntimeMapper productionLineRuntimeMapper;
    @Resource
    private ProductionLineModelConfigMapper productionLineModelConfigMapper;

    @Override
    @Transactional(transactionManager = "apsTransactionManager", rollbackFor = Exception.class)
    public int commitPlan(List<CalendarEventDTO> events) {
        return commitPlan(events, java.util.Collections.emptySet());
    }

    @Override
    @Transactional(transactionManager = "apsTransactionManager", rollbackFor = Exception.class)
    public int commitPlan(List<CalendarEventDTO> events, Set<Long> selectedInsertLineIds) {
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
        Set<Long> selectedLines = Optional.ofNullable(selectedInsertLineIds).orElse(java.util.Collections.emptySet()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!selectedLines.isEmpty()) {
            applyInsertAdjustments(parsedEvents, selectedLines, LocalDateTime.now());
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
        if (!selectedLines.isEmpty()) {
            rollbackLineIds.removeAll(selectedLines);
        }
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
        syncRuntimeFromCommittedPlan(parsedEvents, now);
        log.info("commit plan runtime sync done, batch_no={}, affected_lines={}, inserted_items={}",
                batchNo,
                parsedEvents.stream().map(p -> p.lineId).filter(Objects::nonNull).collect(Collectors.toSet()).size(),
                inserted);

        return inserted;
    }

    private void syncRuntimeFromCommittedPlan(List<ParsedPlanEvent> parsedEvents, Date now) {
        if (parsedEvents == null || parsedEvents.isEmpty() || now == null) {
            return;
        }
        LocalDateTime nowAt = toLocalDateTime(now);
        Map<Long, List<ParsedPlanEvent>> eventsByLine = parsedEvents.stream()
                .filter(p -> p.lineId != null)
                .collect(Collectors.groupingBy(p -> p.lineId));
        if (eventsByLine.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, List<ParsedPlanEvent>> entry : eventsByLine.entrySet()) {
            Long lineId = entry.getKey();
            List<ParsedPlanEvent> lineEvents = entry.getValue();
            lineEvents.sort((left, right) -> {
                int startCmp = right.startDate.compareTo(left.startDate);
                if (startCmp != 0) {
                    return startCmp;
                }
                return left.endDate.compareTo(right.endDate);
            });
            ParsedPlanEvent runningEvent = lineEvents.stream()
                    .filter(e -> !now.before(e.startDate) && now.before(e.endDate))
                    .findFirst()
                    .orElse(null);

            List<ProductionLineRuntime> runtimes = productionLineRuntimeMapper.selectList(lineId);
            ProductionLineRuntime before = runtimes.isEmpty() ? null : runtimes.get(0);
            ProductionLineRuntime runtime = before == null ? new ProductionLineRuntime() : before;
            runtime.setLineId(lineId);

            if (runningEvent != null) {
                runtime.setCurrentModel(runningEvent.model);
                runtime.setCurrentCapacity(resolveRuntimeCapacity(lineId, runningEvent.model, before));
                runtime.setStatus(RuntimeStatus.RUNNING);
            } else {
                runtime.setStatus(RuntimeStatus.IDLE);
            }
            runtime.setUpdateTime(now);

            if (runtime.getId() == null) {
                productionLineRuntimeMapper.insertRuntime(runtime);
            } else {
                productionLineRuntimeMapper.updateRuntime(runtime);
            }

            log.info("runtime synced, line_id={}, now={}, before_status={}, before_model={}, before_capacity={}, after_status={}, after_model={}, after_capacity={}",
                    lineId,
                    nowAt,
                    before == null ? null : before.getStatus(),
                    before == null ? null : before.getCurrentModel(),
                    before == null ? null : before.getCurrentCapacity(),
                    runtime.getStatus(),
                    runtime.getCurrentModel(),
                    runtime.getCurrentCapacity());
        }
    }

    private BigDecimal resolveRuntimeCapacity(Long lineId, String model, ProductionLineRuntime currentRuntime) {
        if (lineId == null || model == null || model.trim().isEmpty()) {
            return currentRuntime == null ? null : currentRuntime.getCurrentCapacity();
        }
        List<ProductionLineModelConfig> configs = productionLineModelConfigMapper.selectPageList(lineId, null, 0L, 1000L);
        Optional<ProductionLineModelConfig> bestConfig = configs.stream()
                .filter(c -> c != null && c.getCapacityPerHour() != null && c.getModel() != null)
                .filter(c -> c.getStatus() == null || c.getStatus() != 0)
                .filter(c -> model.startsWith(c.getModel().trim()))
                .sorted((left, right) -> {
                    int lenCompare = Integer.compare(right.getModel().trim().length(), left.getModel().trim().length());
                    if (lenCompare != 0) {
                        return lenCompare;
                    }
                    Integer leftPriority = left.getPriority() == null ? Integer.MAX_VALUE : left.getPriority();
                    Integer rightPriority = right.getPriority() == null ? Integer.MAX_VALUE : right.getPriority();
                    return Integer.compare(leftPriority, rightPriority);
                })
                .findFirst();
        if (bestConfig.isPresent()) {
            return bestConfig.get().getCapacityPerHour();
        }
        return currentRuntime == null ? null : currentRuntime.getCurrentCapacity();
    }

    private void applyInsertAdjustments(List<ParsedPlanEvent> parsedEvents, Set<Long> selectedLines, LocalDateTime now) {
        Map<Long, List<ParsedPlanEvent>> insertEventsByLine = parsedEvents.stream()
                .filter(p -> p.lineId != null && selectedLines.contains(p.lineId))
                .collect(Collectors.groupingBy(p -> p.lineId));
        if (insertEventsByLine.isEmpty()) {
            return;
        }

        Date nowDate = toDate(now);
        List<ProductionPlanItem> occupiedItems = productionPlanItemMapper.selectList(new LambdaQueryWrapper<ProductionPlanItem>()
                .in(ProductionPlanItem::getLineId, insertEventsByLine.keySet())
                .gt(ProductionPlanItem::getEndDate, nowDate)
                .orderByAsc(ProductionPlanItem::getLineId)
                .orderByAsc(ProductionPlanItem::getStartDate)
                .orderByAsc(ProductionPlanItem::getId));
        Map<Long, List<ProductionPlanItem>> occupiedByLine = occupiedItems.stream()
                .collect(Collectors.groupingBy(ProductionPlanItem::getLineId));

        for (Map.Entry<Long, List<ParsedPlanEvent>> entry : insertEventsByLine.entrySet()) {
            Long lineId = entry.getKey();
            List<ParsedPlanEvent> insertEvents = entry.getValue();
            insertEvents.sort((a, b) -> a.startDate.compareTo(b.startDate));

            LocalDateTime firstPreviewStart = toLocalDateTime(insertEvents.get(0).startDate);
            LocalDateTime cursor = firstPreviewStart.isAfter(now) ? firstPreviewStart : now;
            for (ParsedPlanEvent event : insertEvents) {
                Duration duration = Duration.between(toLocalDateTime(event.startDate), toLocalDateTime(event.endDate));
                if (duration.isNegative() || duration.isZero()) {
                    duration = Duration.ofMinutes(1);
                }
                event.startDate = toDate(cursor);
                cursor = cursor.plus(duration);
                event.endDate = toDate(cursor);
            }
            postponeUnfinishedOnLine(occupiedByLine.getOrDefault(lineId, new ArrayList<>()), now, cursor);
        }
    }

    private void postponeUnfinishedOnLine(List<ProductionPlanItem> occupiedItems, LocalDateTime now, LocalDateTime beginAt) {
        if (occupiedItems == null || occupiedItems.isEmpty()) {
            return;
        }
        List<ProductionPlanItem> toReschedule = new ArrayList<>();
        Date nowDate = toDate(now);
        Date updateAt = new Date();
        for (ProductionPlanItem item : occupiedItems) {
            LocalDateTime start = toLocalDateTime(item.getStartDate());
            LocalDateTime end = toLocalDateTime(item.getEndDate());
            if (!start.isBefore(now)) {
                toReschedule.add(item);
                continue;
            }
            if (!end.isAfter(now)) {
                continue;
            }

            int totalQty = Optional.ofNullable(item.getAssignQty()).orElse(0);
            int totalOrderQty = Optional.ofNullable(item.getOrderDemandQty()).orElse(0);
            int totalSafetyQty = Optional.ofNullable(item.getSafetyDemandQty()).orElse(0);
            long totalSeconds = Math.max(1L, Duration.between(start, end).getSeconds());
            long finishedSeconds = Math.max(0L, Duration.between(start, now).getSeconds());
            int finishedTotalQty = (int) Math.min(totalQty, Math.floor((double) totalQty * finishedSeconds / totalSeconds));
            int finishedOrderQty = (int) Math.min(totalOrderQty, Math.floor((double) totalOrderQty * finishedSeconds / totalSeconds));
            int finishedSafetyQty = Math.max(0, finishedTotalQty - finishedOrderQty);
            if (finishedSafetyQty > totalSafetyQty) {
                finishedSafetyQty = totalSafetyQty;
                finishedOrderQty = Math.max(0, finishedTotalQty - finishedSafetyQty);
            }
            int remainTotal = Math.max(0, totalQty - finishedTotalQty);
            int remainOrder = Math.max(0, totalOrderQty - finishedOrderQty);
            int remainSafety = Math.max(0, totalSafetyQty - finishedSafetyQty);

            if (finishedTotalQty <= 0) {
                productionPlanItemMapper.deleteById(item.getId());
            } else {
                item.setAssignQty(finishedTotalQty);
                item.setOrderDemandQty(finishedOrderQty);
                item.setSafetyDemandQty(finishedSafetyQty);
                item.setEndDate(nowDate);
                item.setUpdatedAt(updateAt);
                productionPlanItemMapper.updateById(item);
            }
            if (remainTotal > 0) {
                ProductionPlanItem remain = cloneItem(item);
                remain.setId(null);
                remain.setStartDate(nowDate);
                remain.setEndDate(toDate(end));
                remain.setAssignQty(remainTotal);
                remain.setOrderDemandQty(remainOrder);
                remain.setSafetyDemandQty(remainSafety);
                remain.setCreatedAt(updateAt);
                remain.setUpdatedAt(updateAt);
                toReschedule.add(remain);
            }
        }

        toReschedule.sort((a, b) -> {
            int c = a.getStartDate().compareTo(b.getStartDate());
            if (c != 0) {
                return c;
            }
            return Optional.ofNullable(a.getId()).orElse(Long.MAX_VALUE)
                    .compareTo(Optional.ofNullable(b.getId()).orElse(Long.MAX_VALUE));
        });

        LocalDateTime cursor = beginAt.isAfter(now) ? beginAt : now;
        for (ProductionPlanItem item : toReschedule) {
            LocalDateTime originalStart = toLocalDateTime(item.getStartDate());
            LocalDateTime originalEnd = toLocalDateTime(item.getEndDate());
            Duration duration = Duration.between(originalStart, originalEnd);
            if (duration.isNegative() || duration.isZero()) {
                duration = Duration.ofMinutes(1);
            }
            item.setStartDate(toDate(cursor));
            cursor = cursor.plus(duration);
            item.setEndDate(toDate(cursor));
            item.setUpdatedAt(updateAt);
            if (item.getId() == null) {
                item.setCreatedAt(updateAt);
                productionPlanItemMapper.insert(item);
            } else {
                productionPlanItemMapper.updateById(item);
            }
        }
    }

    private ProductionPlanItem cloneItem(ProductionPlanItem item) {
        ProductionPlanItem cloned = new ProductionPlanItem();
        cloned.setPlanId(item.getPlanId());
        cloned.setPlanBatchNo(item.getPlanBatchNo());
        cloned.setOrderId(item.getOrderId());
        cloned.setCustomer(item.getCustomer());
        cloned.setModel(item.getModel());
        cloned.setOuterInnerRing(item.getOuterInnerRing());
        cloned.setLineId(item.getLineId());
        cloned.setLineName(item.getLineName());
        cloned.setSource(item.getSource());
        return cloned;
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
