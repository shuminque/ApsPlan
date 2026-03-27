package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.PlanPreviewDailyDTO;
import com.depository_manage.pojo.shift.PlanPreviewOrderDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.utils.CraftMappingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanningServiceImplRegressionTest {

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
        ReflectionTestUtils.setField(service, "productionOrderService", productionOrderService);
        ReflectionTestUtils.setField(service, "safetyStockService", safetyStockService);
        ReflectionTestUtils.setField(service, "shiftCalendarService", shiftCalendarService);
        ReflectionTestUtils.setField(service, "modelConfigMapper", modelConfigMapper);
        ReflectionTestUtils.setField(service, "productionLineMapper", productionLineMapper);
        ReflectionTestUtils.setField(service, "bearingRecordService", bearingRecordService);

        when(safetyStockService.list()).thenReturn(Collections.emptyList());
        when(shiftCalendarService.getSchedulesByDate(anyString())).thenReturn(Collections.emptyList());
        when(bearingRecordService.selectInventoryByCutoffDate(any())).thenReturn(Collections.emptyList());
        when(modelConfigMapper.selectPageList(isNull(), isNull(), anyLong(), anyLong())).thenReturn(Arrays.asList(
                newModelConfig(1L, "6205", "10"),
                newModelConfig(2L, "6205", "6"),
                newModelConfig(3L, "LA6205", "12"),
                newModelConfig(3L, "LB6205", "12")
        ));
        when(productionLineMapper.selectPageList(isNull(), anyLong(), anyLong())).thenReturn(Arrays.asList(
                newLine(1L, "L1", CraftMappingUtil.PIPE_CRAFT),
                newLine(2L, "L2", CraftMappingUtil.PIPE_CRAFT),
                newLine(3L, "L3", CraftMappingUtil.BAR_CRAFT)
        ));
    }

    @Test
    void shouldReturnEmptyWhenNoOpenOrders() {
        when(productionOrderService.list(any())).thenReturn(Collections.emptyList());

        PlanPreviewResponseDTO response = service.generatePlanPreview(LocalDate.now().toString(), LocalDate.now().plusDays(2).toString());

        assertTrue(response.getEvents().isEmpty());
        assertTrue(response.getOrders().isEmpty());
        assertTrue(response.getDailyOutputs().isEmpty());
    }

    @Test
    void shouldSupportAutoAndInsertModes() {
        LocalDate today = LocalDate.now();
        ProductionOrder normal = newOrder(1001L, "C1", "LA", "6205", 120, "普通", today.plusDays(4), CraftMappingUtil.PIPE_CRAFT);
        ProductionOrder insert = newOrder(1002L, "C1", "LA", "6205", 80, "普通", today.plusDays(1), CraftMappingUtil.PIPE_CRAFT);
        when(productionOrderService.list(any())).thenReturn(Arrays.asList(normal, insert));

        PlanPreviewResponseDTO auto = service.generatePlanPreview(today.toString(), today.plusDays(2).toString(),
                "AUTO", Collections.emptyList(), "ALL", Collections.emptyList(), 0, Collections.emptyMap(), "min_line");
        PlanPreviewResponseDTO insertMode = service.generatePlanPreview(today.toString(), today.plusDays(2).toString(),
                "INSERT", Collections.singletonList(1002L), "ALL", Collections.emptyList(), 0, Collections.emptyMap(), "min_line");

        assertFalse(auto.getOrders().isEmpty());
        assertTrue(insertMode.getInsertFulfillmentRate().compareTo(BigDecimal.ONE) >= 0);
    }

    @Test
    void shouldShiftStartByFreezeHours() {
        LocalDate today = LocalDate.now();
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(
                newOrder(1001L, "C1", "LA", "6205", 120, "普通", today.plusDays(2), CraftMappingUtil.PIPE_CRAFT)
        ));

        PlanPreviewResponseDTO response = service.generatePlanPreview(today + "T08:00:00", today.plusDays(1).toString(),
                "AUTO", Collections.emptyList(), "ALL", Collections.emptyList(), 4, Collections.emptyMap(), "min_line");

        assertEquals(today + "T12:00:00", response.getPlanStart());
    }

    @Test
    void shouldRespectPartialLineScope() {
        LocalDate today = LocalDate.now();
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(
                newOrder(1001L, "C1", "LA", "6205", 120, "普通", today.plusDays(2), CraftMappingUtil.PIPE_CRAFT)
        ));

        PlanPreviewResponseDTO response = service.generatePlanPreview(today.toString(), today.plusDays(2).toString(),
                "AUTO", Collections.emptyList(), "PARTIAL", Collections.singletonList(2L), 0, Collections.emptyMap(), "min_line");

        assertFalse(response.getEvents().isEmpty());
        assertTrue(response.getEvents().stream().allMatch(e -> Long.valueOf(2L).equals(e.getLineId())));
    }

    @Test
    void shouldFallbackToDefaultShiftHoursWhenNoShiftCalendar() {
        LocalDate today = LocalDate.now();
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(
                newOrder(1001L, "C1", "LA", "6205", 80, "普通", today.plusDays(2), CraftMappingUtil.PIPE_CRAFT)
        ));

        PlanPreviewResponseDTO response = service.generatePlanPreview(today.toString(), today.plusDays(2).toString());

        assertFalse(response.getDailyOutputs().isEmpty());
    }

    @Test
    void shouldPairLaLbOnBarCraftLine() {
        LocalDate today = LocalDate.now();
        ProductionOrder la = newOrder(2001L, "C-BAR", "LA", "LA6205", 60, "普通", today.plusDays(2), CraftMappingUtil.BAR_CRAFT);
        ProductionOrder lb = newOrder(2002L, "C-BAR", "LB", "LB6205", 60, "普通", today.plusDays(2), CraftMappingUtil.BAR_CRAFT);
        when(productionOrderService.list(any())).thenReturn(Arrays.asList(la, lb));

        PlanPreviewResponseDTO response = service.generatePlanPreview(today.toString(), today.plusDays(2).toString());

        Map<String, Integer> ringTotals = response.getDailyOutputs().stream()
                .collect(Collectors.groupingBy(PlanPreviewDailyDTO::getOuterInnerRing, Collectors.summingInt(PlanPreviewDailyDTO::getQuantity)));
        assertEquals(ringTotals.get("LA"), ringTotals.get("LB"));
    }

    @Test
    void shouldAccumulateDelayedDaysWhenPlanBeyondWindow() {
        LocalDate today = LocalDate.now();
        when(modelConfigMapper.selectPageList(isNull(), isNull(), anyLong(), anyLong())).thenReturn(Collections.singletonList(newModelConfig(1L, "6205", "1")));
        when(productionLineMapper.selectPageList(isNull(), anyLong(), anyLong())).thenReturn(Collections.singletonList(newLine(1L, "L1", CraftMappingUtil.PIPE_CRAFT)));
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(
                newOrder(3001L, "C1", "LA", "6205", 500, "普通", today.plusDays(1), CraftMappingUtil.PIPE_CRAFT)
        ));

        PlanPreviewResponseDTO response = service.generatePlanPreview(today.toString(), today.plusDays(1).toString());

        assertTrue(response.getDelayedDays() > 0);
    }

    @Test
    void plannedQuantityKnownBehaviorShouldEqualRequiredQuantity() {
        LocalDate today = LocalDate.now();
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(
                newOrder(1001L, "C1", "LA", "6205", 120, "普通", today.plusDays(2), CraftMappingUtil.PIPE_CRAFT)
        ));

        PlanPreviewResponseDTO response = service.generatePlanPreview(today.toString(), today.plusDays(2).toString());
        PlanPreviewOrderDTO row = response.getOrders().get(0);

        // KNOWN_BEHAVIOR: 当前 plannedQuantity 与 requiredQuantity 始终保持一致。
        assertEquals(row.getRequiredQuantity(), row.getPlannedQuantity());
    }

    private ProductionOrder newOrder(Long id, String customer, String ring, String model, int qty, String priority, LocalDate due, String craft) {
        ProductionOrder order = new ProductionOrder();
        order.setId(id);
        order.setCustomer(customer);
        order.setOuterInnerRing(ring);
        order.setModel(model);
        order.setCraft(craft);
        order.setQuantity(qty);
        order.setAssignedQuantity(0);
        order.setStatus("0");
        order.setPriority(priority);
        order.setDeliveryDate(Date.from(due.atStartOfDay(ZoneId.systemDefault()).toInstant()));
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
