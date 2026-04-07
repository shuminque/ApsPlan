package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.CalendarEventDTO;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
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
class ProductionPlanningFixedClockTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), TEST_ZONE);
    private static final LocalDate FIXED_TODAY = LocalDate.now(FIXED_CLOCK);

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

        when(safetyStockService.list()).thenReturn(Collections.emptyList());
        when(shiftCalendarService.getSchedulesByDate(anyString())).thenReturn(Collections.emptyList());
        when(bearingRecordService.selectInventoryByCutoffDate(any())).thenReturn(Collections.emptyList());
        when(modelConfigMapper.selectPageList(isNull(), isNull(), anyLong(), anyLong())).thenReturn(Collections.singletonList(newModelConfig(1L, "6205", "10")));
        when(productionLineMapper.selectPageList(isNull(), anyLong(), anyLong())).thenReturn(Collections.singletonList(newLine(1L, "L1", CraftMappingUtil.PIPE_CRAFT)));
    }

    @Test
    void deliveryUrgencyDaysShouldUseInjectedFixedClock() {
        ProductionOrder urgent = newOrder(1001L, "URGENT", "LA", "6205", 20, FIXED_TODAY.plusDays(1));
        ProductionOrder normal = newOrder(1002L, "NORMAL", "LA", "6205", 20, FIXED_TODAY.plusDays(5));
        when(productionOrderService.list(any())).thenReturn(Arrays.asList(normal, urgent));

        PlanPreviewResponseDTO response = service.generatePlanPreview(FIXED_TODAY.toString(), FIXED_TODAY.plusDays(2).toString());

        assertFalse(response.getOrders().isEmpty());
        PlanPreviewOrderDTO first = response.getOrders().get(0);
        assertEquals("URGENT", first.getCustomer(), "deliveryUrgencyDays 应基于固定 Clock 计算并稳定排序");
    }

    @Test
    void shiftTruncationShouldUseInjectedZoneIdAndFixedClock() {
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(
                newOrder(2001L, "C1", "LA", "6205", 120, FIXED_TODAY.plusDays(2))
        ));
        when(shiftCalendarService.getSchedulesByDate(FIXED_TODAY.toString()))
                .thenReturn(Collections.singletonList(newShift(FIXED_TODAY, 8, 16)));

        PlanPreviewResponseDTO response = service.generatePlanPreview(
                FIXED_TODAY + "T12:00:00",
                FIXED_TODAY.plusDays(2).toString(),
                "AUTO",
                Collections.emptyList(),
                "ALL",
                Collections.emptyList(),
                0,
                Collections.emptyMap(),
                "min_line");

        assertFalse(response.getEvents().isEmpty());
        CalendarEventDTO firstEvent = response.getEvents().get(0);
        assertEquals(FIXED_TODAY + "T12:00:00", firstEvent.getStart(), "班次应在固定时间基线下被正确截断到计划开始时刻");
    }

    @Test
    void startDateCorrectionShouldUseInjectedFixedClock() {
        when(productionOrderService.list(any())).thenReturn(Collections.singletonList(
                newOrder(3001L, "C1", "LA", "6205", 40, FIXED_TODAY.plusDays(1))
        ));

        LocalDate past = FIXED_TODAY.minusDays(3);
        PlanPreviewResponseDTO response = service.generatePlanPreview(past.toString(), FIXED_TODAY.plusDays(1).toString());

        assertTrue(response.getPlanStart().startsWith(FIXED_TODAY.toString()), "过去 startDate 必须基于注入 Clock 纠偏到固定当天");
    }

    private ProductionOrder newOrder(Long id, String customer, String ring, String model, int qty, LocalDate due) {
        ProductionOrder order = new ProductionOrder();
        order.setId(id);
        order.setCustomer(customer);
        order.setOuterInnerRing(ring);
        order.setModel(model);
        order.setCraft(CraftMappingUtil.PIPE_CRAFT);
        order.setQuantity(qty);
        order.setAssignedQuantity(0);
        order.setStatus("0");
        order.setPriority("普通");
        order.setDeliveryDate(Date.from(due.atStartOfDay(TEST_ZONE).toInstant()));
        return order;
    }

    private ShiftSchedule newShift(LocalDate day, int startHour, int endHour) {
        ShiftSchedule schedule = new ShiftSchedule();
        schedule.setScheduleDate(Date.from(day.atStartOfDay(TEST_ZONE).toInstant()));
        schedule.setStartDateTime(Date.from(day.atTime(startHour, 0).atZone(TEST_ZONE).toInstant()));
        schedule.setEndDateTime(Date.from(day.atTime(endHour, 0).atZone(TEST_ZONE).toInstant()));
        return schedule;
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
        line.setStatus(0);
        return line;
    }
}
