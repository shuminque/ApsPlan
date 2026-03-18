package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.pojo.shift.PlanPreviewOrderDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanningServiceImplTest {

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

    @InjectMocks
    private ProductionPlanningServiceImpl service;

    @Test
    void barCraftLineShouldPlanLaAndLbTogether() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(2);

        when(productionOrderService.list(any())).thenReturn(Arrays.asList(
                order("昆山NSK", "LA", "6200VV*XC2", 1600, start.plusDays(5)),
                order("昆山NSK", "LB", "6200VV*XC2", 1600, start.plusDays(5))
        ));
        when(safetyStockService.list()).thenReturn(Collections.emptyList());
        when(shiftCalendarService.getSchedulesByDate(any())).thenReturn(Collections.emptyList());
        when(bearingRecordService.selectInventoryByCutoffDate(anyMap())).thenReturn(Collections.emptyList());
        when(productionLineMapper.selectPageList(any(), any(), any())).thenReturn(Collections.singletonList(
                line(1L, "A线", "棒材工艺")
        ));
        when(modelConfigMapper.selectPageList(any(), any(), any(), any())).thenReturn(Collections.singletonList(
                config(1L, "6200", 100)
        ));

        PlanPreviewResponseDTO response = service.generatePlanPreview(start.toString(), end.toString());
        List<PlanPreviewOrderDTO> orders = response.getOrders();

        assertEquals(2, orders.size());
        assertEquals(1600, plannedQty(orders, "LA"));
        assertEquals(1600, plannedQty(orders, "LB"));
        assertEquals(4, response.getDailyOutputs().size());
        assertEquals(2, response.getEvents().size());
        assertTrue(response.getDailyOutputs().stream().allMatch(item -> item.getQuantity() == 800));
    }

    @Test
    void remainingDemandCanFallbackToTubeCraftSeparatePlanning() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(2);

        when(productionOrderService.list(any())).thenReturn(Arrays.asList(
                order("昆山NSK", "LA", "6200VV*XC2", 800, start.plusDays(5)),
                order("昆山NSK", "LB", "6200VV*XC2", 1600, start.plusDays(5))
        ));
        when(safetyStockService.list()).thenReturn(Collections.emptyList());
        when(shiftCalendarService.getSchedulesByDate(any())).thenReturn(Collections.emptyList());
        when(bearingRecordService.selectInventoryByCutoffDate(anyMap())).thenReturn(Collections.emptyList());
        when(productionLineMapper.selectPageList(any(), any(), any())).thenReturn(Arrays.asList(
                line(1L, "A线", "棒材工艺"),
                line(2L, "B线", "管材工艺")
        ));
        when(modelConfigMapper.selectPageList(any(), any(), any(), any())).thenReturn(Arrays.asList(
                config(1L, "6200", 100),
                config(2L, "6200", 100)
        ));

        PlanPreviewResponseDTO response = service.generatePlanPreview(start.toString(), end.toString());
        List<PlanPreviewOrderDTO> orders = response.getOrders();

        assertEquals(800, plannedQty(orders, "LA"));
        assertEquals(1600, plannedQty(orders, "LB"));
        assertEquals(Arrays.asList("LA", "LB", "LB"),
                response.getDailyOutputs().stream()
                        .map(item -> item.getOuterInnerRing())
                        .sorted()
                        .collect(Collectors.toList()));
    }

    private Integer plannedQty(List<PlanPreviewOrderDTO> orders, String ring) {
        return orders.stream()
                .filter(item -> ring.equals(item.getOuterInnerRing()))
                .findFirst()
                .orElseThrow(AssertionError::new)
                .getPlannedQuantity();
    }

    private ProductionOrder order(String customer, String ring, String model, int quantity, LocalDate deliveryDate) {
        ProductionOrder order = new ProductionOrder();
        order.setCustomer(customer);
        order.setOuterInnerRing(ring);
        order.setModel(model);
        order.setQuantity(quantity);
        order.setAssignedQuantity(0);
        order.setStatus("0");
        order.setDeliveryDate(Date.from(deliveryDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return order;
    }

    private ProductionLine line(Long id, String lineName, String craft) {
        ProductionLine line = new ProductionLine();
        line.setId(id);
        line.setLineName(lineName);
        line.setCraft(craft);
        line.setStatus(1);
        return line;
    }

    private ProductionLineModelConfig config(Long lineId, String model, int capacityPerHour) {
        ProductionLineModelConfig config = new ProductionLineModelConfig();
        config.setLineId(lineId);
        config.setModel(model);
        config.setCapacityPerHour(BigDecimal.valueOf(capacityPerHour));
        config.setPriority(1);
        config.setStatus(1);
        return config;
    }
}
