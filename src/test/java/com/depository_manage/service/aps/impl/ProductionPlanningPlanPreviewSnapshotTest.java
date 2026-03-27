package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewDailyDTO;
import com.depository_manage.pojo.shift.PlanPreviewOrderDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.utils.CraftMappingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanningPlanPreviewSnapshotTest {
    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), TEST_ZONE);

    @Mock
    private ProductionOrderService productionOrderService;
    @Mock
    private SafetyStockService safetyStockService;
    @Mock
    private ShiftCalendarService shiftCalendarService;
    @Mock
    private ProductionLineModelConfigMapper modelConfigMapper;
    @Mock
    private ProductionLineMapper productionLineMapper;
    @Mock
    private BearingRecordService bearingRecordService;

    private ProductionPlanningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductionPlanningServiceImpl();
        service.setClock(FIXED_CLOCK);
        service.setZoneId(TEST_ZONE);
        ReflectionTestUtils.setField(service, "productionOrderService", productionOrderService);
        ReflectionTestUtils.setField(service, "safetyStockService", safetyStockService);
        ReflectionTestUtils.setField(service, "shiftCalendarService", shiftCalendarService);
        ReflectionTestUtils.setField(service, "modelConfigMapper", modelConfigMapper);
        ReflectionTestUtils.setField(service, "productionLineMapper", productionLineMapper);
        ReflectionTestUtils.setField(service, "bearingRecordService", bearingRecordService);

        LocalDate today = LocalDate.now(FIXED_CLOCK);
        when(productionOrderService.list(any())).thenReturn(Arrays.asList(
                newOrder(1001L, "S1", "LA", "6205", 80, today.plusDays(2), CraftMappingUtil.PIPE_CRAFT),
                newOrder(1002L, "S1", "LB", "6205", 50, today.plusDays(3), CraftMappingUtil.PIPE_CRAFT)
        ));
        when(safetyStockService.list()).thenReturn(Collections.emptyList());
        when(shiftCalendarService.getSchedulesByDate(anyString())).thenReturn(Collections.emptyList());
        when(bearingRecordService.selectInventoryByCutoffDate(any())).thenReturn(Collections.emptyList());
        when(modelConfigMapper.selectPageList(isNull(), isNull(), anyLong(), anyLong())).thenReturn(Arrays.asList(
                newModelConfig(1L, "6205", "10"),
                newModelConfig(2L, "6205", "6")
        ));
        when(productionLineMapper.selectPageList(isNull(), anyLong(), anyLong())).thenReturn(Arrays.asList(
                newLine(1L, "L1", CraftMappingUtil.PIPE_CRAFT),
                newLine(2L, "L2", CraftMappingUtil.PIPE_CRAFT)
        ));
    }

    @Test
    void shouldMatchPlanPreviewSnapshot() throws Exception {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        PlanPreviewResponseDTO response = service.generatePlanPreview(today.toString(), today.plusDays(4).toString());

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String actual = mapper.writeValueAsString(toSnapshot(response));
        String expected = new String(Files.readAllBytes(Paths.get("src/test/resources/snapshots/production_planning_preview_snapshot.json")), StandardCharsets.UTF_8);

        assertEquals(expected.trim(), actual.trim());
    }

    private Map<String, Object> toSnapshot(PlanPreviewResponseDTO response) {
        Map<String, Object> snap = new LinkedHashMap<String, Object>();
        snap.put("events", response.getEvents().stream().map(this::eventNode).collect(Collectors.toList()));
        snap.put("orders", response.getOrders().stream().map(this::orderNode).collect(Collectors.toList()));
        snap.put("dailyOutputs", response.getDailyOutputs().stream().map(this::dailyNode).collect(Collectors.toList()));

        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        metrics.put("planStart", normalizeTimeline(response.getPlanStart()));
        metrics.put("planEnd", normalizeTimeline(response.getPlanEnd()));
        metrics.put("squeezedOrderCount", response.getSqueezedOrderCount());
        metrics.put("delayedDays", response.getDelayedDays());
        metrics.put("insertFulfillmentRate", response.getInsertFulfillmentRate());
        snap.put("metrics", metrics);
        return snap;
    }

    private Map<String, Object> eventNode(CalendarEventDTO e) {
        Map<String, Object> node = new LinkedHashMap<String, Object>();
        node.put("title", e.getTitle());
        node.put("start", normalizeTimeline(e.getStart()));
        node.put("end", normalizeTimeline(e.getEnd()));
        node.put("lineId", e.getLineId());
        node.put("lineName", e.getLineName());
        node.put("customer", e.getCustomer());
        node.put("outerInnerRing", e.getOuterInnerRing());
        node.put("model", e.getModel());
        node.put("quantity", e.getQuantity());
        return node;
    }

    private Map<String, Object> orderNode(PlanPreviewOrderDTO o) {
        Map<String, Object> node = new LinkedHashMap<String, Object>();
        node.put("customer", o.getCustomer());
        node.put("outerInnerRing", o.getOuterInnerRing());
        node.put("model", o.getModel());
        node.put("requiredQuantity", o.getRequiredQuantity());
        node.put("plannedQuantity", o.getPlannedQuantity());
        node.put("plannedDays", o.getPlannedDays());
        node.put("orderIds", o.getOrderIds());
        return node;
    }

    private Map<String, Object> dailyNode(PlanPreviewDailyDTO d) {
        Map<String, Object> node = new LinkedHashMap<String, Object>();
        node.put("day", normalizeTimeline(d.getDay()));
        node.put("lineName", d.getLineName());
        node.put("customer", d.getCustomer());
        node.put("outerInnerRing", d.getOuterInnerRing());
        node.put("model", d.getModel());
        node.put("quantity", d.getQuantity());
        return node;
    }


    private String normalizeTimeline(String value) {
        if (value == null) {
            return null;
        }
        LocalDate base = LocalDate.now(FIXED_CLOCK);
        for (int i = 0; i <= 15; i++) {
            String day = base.plusDays(i).toString();
            value = value.replace(day, "D" + i);
        }
        return value;
    }

    private ProductionOrder newOrder(Long id, String customer, String ring, String model, int qty, LocalDate due, String craft) {
        ProductionOrder order = new ProductionOrder();
        order.setId(id);
        order.setCustomer(customer);
        order.setOuterInnerRing(ring);
        order.setModel(model);
        order.setCraft(craft);
        order.setQuantity(qty);
        order.setAssignedQuantity(0);
        order.setStatus("0");
        order.setPriority("普通");
        order.setDeliveryDate(Date.from(due.atStartOfDay(TEST_ZONE).toInstant()));
        return order;
    }

    private ProductionLineModelConfig newModelConfig(Long lineId, String model, String capacityPerHour) {
        ProductionLineModelConfig config = new ProductionLineModelConfig();
        config.setLineId(lineId);
        config.setModel(model);
        config.setCapacityPerHour(new BigDecimal(capacityPerHour));
        config.setPriority(1);
        config.setStatus(1);
        return config;
    }

    private ProductionLine newLine(Long lineId, String lineName, String craft) {
        ProductionLine line = new ProductionLine();
        line.setId(lineId);
        line.setLineName(lineName);
        line.setCraft(craft);
        line.setStatus(1);
        return line;
    }
}
