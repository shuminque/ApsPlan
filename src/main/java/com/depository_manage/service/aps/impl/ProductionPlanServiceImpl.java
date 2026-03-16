package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ProductionPlan;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionOrderMapper;
import com.depository_manage.mapper.aps.ProductionPlanItemMapper;
import com.depository_manage.mapper.aps.ProductionPlanMapper;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.service.aps.ProductionPlanService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductionPlanServiceImpl implements ProductionPlanService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern PLAN_TITLE_PATTERN = Pattern.compile("^(.*?)\\s+(.*?)\\/(.*?)\\s+(.*?)\\s+x\\s+([0-9,]+)$");

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

        Map<String, List<ProductionOrder>> orderGroupMap = loadOpenOrdersByKey();
        int inserted = 0;
        for (ParsedPlanEvent parsed : parsedEvents) {
            List<ProductionOrder> candidates = orderGroupMap.getOrDefault(parsed.orderKey(), new ArrayList<>());
            if (candidates.isEmpty()) {
                continue;
            }
            int left = parsed.assignQty;
            for (ProductionOrder order : candidates) {
                if (left <= 0) {
                    break;
                }
                int assigned = Optional.ofNullable(order.getAssignedQuantity()).orElse(0);
                int quantity = Optional.ofNullable(order.getQuantity()).orElse(0);
                int remain = Math.max(0, quantity - assigned);
                if (remain <= 0) {
                    continue;
                }
                int assignToOrder = Math.min(left, remain);

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
                item.setSource("RULE_PRIORITY");
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                productionPlanItemMapper.insert(item);
                inserted++;

                order.setAssignedQuantity(assigned + assignToOrder);
                order.setStatus("已排产");
                productionOrderMapper.updateById(order);
                left -= assignToOrder;
            }
        }

        return inserted;
    }

    private Map<String, List<ProductionOrder>> loadOpenOrdersByKey() {
        List<ProductionOrder> orders = productionOrderMapper.selectList(new LambdaQueryWrapper<ProductionOrder>()
                .in(ProductionOrder::getStatus, "待排产", "已排产")
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
            map.computeIfAbsent(order.getCustomer() + "|" + order.getOuterInnerRing() + "|" + order.getModel(), k -> new ArrayList<>())
                    .add(order);
        }
        return map;
    }

    private ParsedPlanEvent parseEvent(CalendarEventDTO event) {
        if (event == null || event.getTitle() == null || event.getStart() == null || event.getEnd() == null) {
            return null;
        }
        String title = event.getTitle().trim();
        if (title.startsWith("[排产]")) {
            title = title.substring("[排产]".length()).trim();
        }
        Matcher matcher = PLAN_TITLE_PATTERN.matcher(title);
        if (!matcher.matches()) {
            return null;
        }

        String lineName = matcher.group(1).trim();
        String customer = matcher.group(2).trim();
        String outerInnerRing = matcher.group(3).trim();
        String model = matcher.group(4).trim();
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
        parsed.startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
        parsed.endDate = Date.from(end.atZone(ZoneId.systemDefault()).toInstant());
        return parsed;
    }

    private Long resolveLineId(String lineName) {
        if (lineName == null || lineName.isEmpty()) {
            return null;
        }
        List<ProductionLine> lines = productionLineMapper.selectPageList(lineName, 0L, 1L);
        if (lines.isEmpty()) {
            return null;
        }
        return lines.get(0).getId();
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

        private String orderKey() {
            return customer + "|" + outerInnerRing + "|" + model;
        }
    }
}
