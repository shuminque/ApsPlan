package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.PlanPreviewOrderDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanningBehaviorFreezeTest {

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
        when(modelConfigMapper.selectPageList(isNull(), isNull(), anyLong(), anyLong())).thenReturn(Collections.singletonList(newModelConfig(1L, "6205", "10")));
        when(productionLineMapper.selectPageList(isNull(), anyLong(), anyLong())).thenReturn(Collections.singletonList(newLine(1L, "L1")));
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(newOpenOrder()));
    }

    @Test
    void plannedQuantityShouldStayEqualToRequiredQuantity() {
        LocalDate today = LocalDate.now();
        PlanPreviewResponseDTO response = service.generatePlanPreview(today.toString(), today.plusDays(2).toString());

        assertFalse(response.getOrders().isEmpty());
        PlanPreviewOrderDTO row = response.getOrders().get(0);
        assertEquals(row.getRequiredQuantity(), row.getPlannedQuantity(), "BF-001: plannedQuantity 必须继续与 requiredQuantity 保持一致");
    }

    @Test
    void dateCorrectionShouldClampStartDateToToday() {
        LocalDate today = LocalDate.now();
        LocalDate pastDay = today.minusDays(3);
        PlanPreviewResponseDTO response = service.generatePlanPreview(pastDay.toString(), pastDay.plusDays(1).toString());

        assertTrue(response.getPlanStart().startsWith(today.toString()), "BF-002: 过去 startDate 必须被纠偏到当天");
    }

    @Test
    void freezeHoursShouldShiftPlanStartWhenScheduling() {
        LocalDate today = LocalDate.now();
        String startAt = today + "T08:00:00";
        PlanPreviewResponseDTO response = service.generatePlanPreview(
                startAt,
                today.plusDays(1).toString(),
                "AUTO",
                Collections.emptyList(),
                "ALL",
                Collections.emptyList(),
                4,
                Collections.emptyMap(),
                "min_line");

        assertEquals(today + "T12:00:00", response.getPlanStart(), "BF-003: freezeHours 必须将实际排产起点推迟到 requestStart+freezeHours");
    }

    @Test
    void lineScopeShouldDefaultToAllWhenNotPartial() {
        LocalDate today = LocalDate.now();
        PlanPreviewResponseDTO response = service.generatePlanPreview(
                today.toString(),
                today.plusDays(1).toString(),
                "AUTO",
                Collections.emptyList(),
                "ALL",
                Collections.singletonList(999L),
                0,
                Collections.emptyMap(),
                "min_line");

        assertFalse(response.getEvents().isEmpty(), "BF-004: 非 PARTIAL 模式必须忽略 lineIds，默认按 ALL 处理");
    }

    private ProductionOrder newOpenOrder() {
        ProductionOrder order = new ProductionOrder();
        order.setId(1001L);
        order.setCustomer("C1");
        order.setOuterInnerRing("LA");
        order.setModel("6205");
        order.setQuantity(120);
        order.setAssignedQuantity(0);
        order.setStatus("0");
        order.setPriority("普通");
        order.setDeliveryDate(Date.from(LocalDate.now().plusDays(5)
                .atStartOfDay(ZoneId.systemDefault()).toInstant()));
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

    private ProductionLine newLine(Long lineId, String lineName) {
        ProductionLine line = new ProductionLine();
        line.setId(lineId);
        line.setLineName(lineName);
        line.setStatus(1);
        return line;
    }
}
