package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionLineRuntime;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.mapper.aps.ProductionLineRuntimeMapper;
import com.depository_manage.mapper.aps.ProductionPlanItemMapper;
import com.depository_manage.pojo.shift.OrderSchedulingEvaluationDTO;
import com.depository_manage.service.aps.ShiftCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderSchedulingEvaluationServiceImplTest {

    private final ProductionLineMapper productionLineMapper = mock(ProductionLineMapper.class);
    private final ProductionLineModelConfigMapper modelConfigMapper = mock(ProductionLineModelConfigMapper.class);
    private final ProductionPlanItemMapper planItemMapper = mock(ProductionPlanItemMapper.class);
    private final ProductionLineRuntimeMapper runtimeMapper = mock(ProductionLineRuntimeMapper.class);
    private final ShiftCalendarService shiftCalendarService = mock(ShiftCalendarService.class);

    private OrderSchedulingEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderSchedulingEvaluationServiceImpl();
        ReflectionTestUtils.setField(service, "productionLineMapper", productionLineMapper);
        ReflectionTestUtils.setField(service, "modelConfigMapper", modelConfigMapper);
        ReflectionTestUtils.setField(service, "productionPlanItemMapper", planItemMapper);
        ReflectionTestUtils.setField(service, "productionLineRuntimeMapper", runtimeMapper);
        ReflectionTestUtils.setField(service, "shiftCalendarService", shiftCalendarService);
        service.setClock(Clock.fixed(Instant.parse("2026-01-01T08:00:00Z"), ZoneId.of("UTC")));
        service.setZoneId(ZoneId.of("UTC"));
    }

    @Test
    void shouldEvaluateAndAllocateWhenFreeCapacityEnough() {
        when(productionLineMapper.selectPageList(null, 0L, 2000L)).thenReturn(Arrays.asList(
                line(1L, "L1", "棒料", 0),
                line(2L, "L2", "棒料", 0)
        ));
        when(modelConfigMapper.selectPageList(null, null, 0L, 5000L)).thenReturn(Arrays.asList(
                cfg(1L, "6205", 60, 1, 1),
                cfg(2L, "6205", 30, 2, 1)
        ));
        when(shiftCalendarService.getSchedulesByDate("2026-01-01")).thenReturn(Collections.singletonList(
                shift(101L, "2026-01-01T08:00:00", "2026-01-01T20:00:00")
        ));
        when(shiftCalendarService.getSchedulesByDate("2025-12-31")).thenReturn(Collections.emptyList());
        when(planItemMapper.selectList(any())).thenReturn(Collections.singletonList(
                plan(1L, "2026-01-01T10:00:00", "2026-01-01T12:00:00")
        ));

        OrderSchedulingEvaluationDTO dto = service.evaluate("6205-2RS", "棒料", 800, LocalDate.parse("2026-01-01"));

        assertEquals(OrderSchedulingEvaluationDTO.Stage.FREE_OK, dto.getStage());
        assertEquals(900, dto.getFreeCapacityQtyBeforeDue());
        assertEquals(2, dto.getAllocationSuggestions().size());
        assertEquals(600, dto.getAllocationSuggestions().get(0).getAllocatedQty());
        assertEquals(200, dto.getAllocationSuggestions().get(1).getAllocatedQty());
    }

    @Test
    void shouldReturnDelayRequiredWhenNoCandidateLine() {
        when(productionLineMapper.selectPageList(null, 0L, 2000L)).thenReturn(Collections.singletonList(
                line(3L, "L3", "管料", 0)
        ));
        when(modelConfigMapper.selectPageList(null, null, 0L, 5000L)).thenReturn(Collections.singletonList(
                cfg(3L, "6205", 50, 1, 1)
        ));

        OrderSchedulingEvaluationDTO dto = service.evaluate("6205", "棒料", 100, LocalDate.parse("2026-01-01"));

        assertEquals(OrderSchedulingEvaluationDTO.Stage.DELAY_REQUIRED, dto.getStage());
        assertEquals(0, dto.getFreeCapacityQtyBeforeDue());
        assertTrue(dto.getLineFreeCapacities().isEmpty());
    }

    @Test
    void shouldReturnPreemptCandidatesWhenGapCanBeCovered() {
        when(productionLineMapper.selectPageList(null, 0L, 2000L)).thenReturn(Collections.singletonList(
                line(1L, "L1", "棒料", 0)
        ));
        when(modelConfigMapper.selectPageList(null, null, 0L, 5000L)).thenReturn(Collections.singletonList(
                cfg(1L, "6205", 60, 1, 1)
        ));
        when(shiftCalendarService.getSchedulesByDate("2026-01-01")).thenReturn(Collections.singletonList(
                shift(101L, "2026-01-01T08:00:00", "2026-01-01T20:00:00")
        ));
        when(shiftCalendarService.getSchedulesByDate("2025-12-31")).thenReturn(Collections.emptyList());

        ProductionPlanItem occupied = plan(1L, "2026-01-01T08:00:00", "2026-01-01T20:00:00");
        when(planItemMapper.selectList(any())).thenReturn(Collections.singletonList(occupied));

        ProductionLineRuntime runtime = new ProductionLineRuntime();
        runtime.setLineId(1L);
        runtime.setLineName("L1");
        runtime.setStatus(1);
        runtime.setCurrentCapacity(BigDecimal.valueOf(60));
        when(runtimeMapper.selectList(null)).thenReturn(Collections.singletonList(runtime));

        occupied.setId(1001L);
        occupied.setModel("6205-2RS");
        occupied.setAssignQty(800);
        occupied.setOrderDemandQty(120);

        OrderSchedulingEvaluationDTO dto = service.evaluate("6205-2RS", "棒料", 900, LocalDate.parse("2026-01-01"));

        assertEquals(OrderSchedulingEvaluationDTO.Stage.PREEMPT_REQUIRED, dto.getStage());
        assertEquals(1, dto.getRequiredPreemptLineCount());
        assertEquals(1, dto.getPreemptCandidates().size());
        assertEquals(480, dto.getPreemptCandidates().get(0).getEstimatedOutput());
        assertEquals(320, dto.getPreemptCandidates().get(0).getReleasableCapacityQty());
        assertEquals(320, dto.getPreemptCandidates().get(0).getImpactDelayMinutes());
    }

    @Test
    void shouldCalculateMinimalRequiredPreemptLineCountByDescendingReleasableQty() {
        when(productionLineMapper.selectPageList(null, 0L, 2000L)).thenReturn(Collections.singletonList(
                line(1L, "L1", "棒料", 0)
        ));
        when(modelConfigMapper.selectPageList(null, null, 0L, 5000L)).thenReturn(Collections.singletonList(
                cfg(1L, "6205", 60, 1, 1)
        ));
        when(shiftCalendarService.getSchedulesByDate("2026-01-01")).thenReturn(Collections.singletonList(
                shift(101L, "2026-01-01T08:00:00", "2026-01-01T20:00:00")
        ));
        when(shiftCalendarService.getSchedulesByDate("2025-12-31")).thenReturn(Collections.emptyList());

        ProductionPlanItem occupied = plan(1L, "2026-01-01T08:00:00", "2026-01-01T20:00:00");
        occupied.setAssignQty(900);
        occupied.setOrderDemandQty(300);
        when(planItemMapper.selectList(any())).thenReturn(Collections.singletonList(occupied));

        ProductionLineRuntime runtime = new ProductionLineRuntime();
        runtime.setLineId(1L);
        runtime.setLineName("L1");
        runtime.setStatus(1);
        runtime.setCurrentCapacity(BigDecimal.valueOf(60));
        when(runtimeMapper.selectList(null)).thenReturn(Collections.singletonList(runtime));

        OrderSchedulingEvaluationDTO dto = service.evaluate("6205-2RS", "棒料", 1100, LocalDate.parse("2026-01-01"));

        assertEquals(OrderSchedulingEvaluationDTO.Stage.PREEMPT_REQUIRED, dto.getStage());
        assertEquals(1, dto.getRequiredPreemptLineCount());
    }

    @Test
    void shouldReturnDelayRequiredWhenAllCandidatesStillCannotFillGap() {
        when(productionLineMapper.selectPageList(null, 0L, 2000L)).thenReturn(Collections.singletonList(
                line(1L, "L1", "棒料", 0)
        ));
        when(modelConfigMapper.selectPageList(null, null, 0L, 5000L)).thenReturn(Collections.singletonList(
                cfg(1L, "6205", 60, 1, 1)
        ));
        when(shiftCalendarService.getSchedulesByDate("2026-01-01")).thenReturn(Collections.singletonList(
                shift(101L, "2026-01-01T08:00:00", "2026-01-01T20:00:00")
        ));
        when(shiftCalendarService.getSchedulesByDate("2025-12-31")).thenReturn(Collections.emptyList());

        ProductionPlanItem occupied = plan(1L, "2026-01-01T08:00:00", "2026-01-01T20:00:00");
        occupied.setAssignQty(700);
        occupied.setOrderDemandQty(450);
        when(planItemMapper.selectList(any())).thenReturn(Collections.singletonList(occupied));

        ProductionLineRuntime runtime = new ProductionLineRuntime();
        runtime.setLineId(1L);
        runtime.setLineName("L1");
        runtime.setStatus(1);
        runtime.setCurrentCapacity(BigDecimal.valueOf(60));
        when(runtimeMapper.selectList(null)).thenReturn(Collections.singletonList(runtime));

        OrderSchedulingEvaluationDTO dto = service.evaluate("6205-2RS", "棒料", 1300, LocalDate.parse("2026-01-01"));

        assertEquals(OrderSchedulingEvaluationDTO.Stage.DELAY_REQUIRED, dto.getStage());
        assertEquals(0, dto.getRequiredPreemptLineCount());
    }

    private ProductionLine line(Long id, String name, String craft, int status) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineName(name);
        line.setCraft(craft);
        line.setStatus(status);
        return line;
    }

    private ProductionLineModelConfig cfg(Long lineId, String model, int cph, Integer priority, Integer status) {
        ProductionLineModelConfig cfg = new ProductionLineModelConfig();
        cfg.setLineId(lineId);
        cfg.setModel(model);
        cfg.setCapacityPerHour(BigDecimal.valueOf(cph));
        cfg.setPriority(priority);
        cfg.setStatus(status);
        return cfg;
    }

    private ShiftSchedule shift(Long id, String start, String end) {
        ShiftSchedule schedule = new ShiftSchedule();
        schedule.setScheduleId(id);
        schedule.setStartDateTime(toDate(start));
        schedule.setEndDateTime(toDate(end));
        return schedule;
    }

    private ProductionPlanItem plan(Long lineId, String start, String end) {
        ProductionPlanItem item = new ProductionPlanItem();
        item.setLineId(lineId);
        item.setStartDate(toDate(start));
        item.setEndDate(toDate(end));
        item.setAssignQty(100);
        item.setOrderDemandQty(100);
        return item;
    }

    private Date toDate(String value) {
        return Date.from(LocalDateTime.parse(value).atZone(ZoneId.of("UTC")).toInstant());
    }
}
