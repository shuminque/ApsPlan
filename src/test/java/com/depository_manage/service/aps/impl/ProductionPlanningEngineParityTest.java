package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.service.aps.planning.NormalizedPlanningRequest;
import com.depository_manage.service.aps.planning.PlanningRequest;
import com.depository_manage.service.aps.planning.PlanningRequestNormalizer;
import com.depository_manage.utils.CraftMappingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanningEngineParityTest {

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
        ReflectionTestUtils.setField(service, "clock", Clock.systemUTC());
        ReflectionTestUtils.setField(service, "zoneId", ZoneId.of("UTC"));

        when(safetyStockService.list()).thenReturn(Collections.emptyList());
        when(shiftCalendarService.getSchedulesByDate(anyString())).thenReturn(Collections.emptyList());
        when(bearingRecordService.selectInventoryByCutoffDate(any())).thenReturn(Collections.emptyList());
        when(modelConfigMapper.selectPageList(isNull(), isNull(), anyLong(), anyLong())).thenReturn(Arrays.asList(
                newModelConfig(1L, "6205", "10"),
                newModelConfig(2L, "LA6205", "8"),
                newModelConfig(2L, "LB6205", "8")
        ));
        when(productionLineMapper.selectPageList(isNull(), anyLong(), anyLong())).thenReturn(Arrays.asList(
                newLine(1L, "L1", CraftMappingUtil.PIPE_CRAFT),
                newLine(2L, "L2", CraftMappingUtil.BAR_CRAFT)
        ));
    }

    @Test
    void v2ShouldMatchV1WithinFreezeBehaviorAndShadowShouldReturnV1() {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        when(productionOrderService.list(any())).thenReturn(Arrays.asList(
                newOrder(1001L, "C1", "LA", "6205", 120, "普通", today.plusDays(2), CraftMappingUtil.PIPE_CRAFT),
                newOrder(1002L, "C1", "LA", "LA6205", 60, "普通", today.plusDays(2), CraftMappingUtil.BAR_CRAFT),
                newOrder(1003L, "C1", "LB", "LB6205", 60, "普通", today.plusDays(2), CraftMappingUtil.BAR_CRAFT)
        ));

        PlanningRequest req = new PlanningRequest(today.toString(), today.plusDays(2).toString(), "AUTO",
                Collections.emptyList(), "ALL", Collections.emptyList(), 0, Collections.emptyMap(), "min_line");
        NormalizedPlanningRequest normalized = new PlanningRequestNormalizer().normalize(req, Clock.systemUTC());
        PlanningContext context = (PlanningContext) ReflectionTestUtils.invokeMethod(service, "createPlanningContext", normalized);

        PlanningResult v1Result = new PlanningEngineV1().plan(context);
        PlanningResult v2Result = new PlanningEngineV2().plan(context);

        assertEquals(sliceSummary(v1Result.getSlices()), sliceSummary(v2Result.getSlices()));
        assertEquals(metricSummary(v1Result), metricSummary(v2Result));

        ReflectionTestUtils.setField(service, "planningEngineMode", "v1");
        PlanPreviewResponseDTO v1Dto = service.generatePlanPreview(req);
        ReflectionTestUtils.setField(service, "planningEngineMode", "v2");
        PlanPreviewResponseDTO v2Dto = service.generatePlanPreview(req);
        ReflectionTestUtils.setField(service, "planningEngineMode", "shadow");
        PlanPreviewResponseDTO shadowDto = service.generatePlanPreview(req);

        assertEquals(dtoSummary(v1Dto), dtoSummary(v2Dto));
        assertEquals(dtoSummary(v1Dto), dtoSummary(shadowDto));
    }

    private String sliceSummary(List<PlanSlice> slices) {
        return slices.stream()
                .map(slice -> String.join("|", String.valueOf(slice.day()), String.valueOf(slice.lineId()),
                        String.valueOf(slice.customer()), String.valueOf(slice.outerInnerRing()),
                        String.valueOf(slice.model()), String.valueOf(slice.quantity())))
                .collect(Collectors.joining(","));
    }

    private String metricSummary(PlanningResult result) {
        PlanningResult.Metrics metrics = new PlanningResultMapper(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME, "#FFB020")
                .calculateMetrics(result.getDemands(), LocalDate.now(Clock.systemUTC()).plusDays(1));
        return metrics.getSqueezedOrderCount() + "|" + metrics.getDelayedDays() + "|" + metrics.getInsertFulfillmentRate();
    }

    private String dtoSummary(PlanPreviewResponseDTO dto) {
        return dto.getEvents().size() + "|" + dto.getOrders().size() + "|" + dto.getDailyOutputs().size() + "|"
                + dto.getPlanStart() + "|" + dto.getPlanEnd() + "|" + dto.getSqueezedOrderCount() + "|"
                + dto.getDelayedDays() + "|" + dto.getInsertFulfillmentRate();
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
        order.setDeliveryDate(Date.from(due.atStartOfDay(ZoneId.of("UTC")).toInstant()));
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
