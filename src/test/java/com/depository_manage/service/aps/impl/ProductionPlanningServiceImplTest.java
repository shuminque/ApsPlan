package com.depository_manage.service.aps.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionPlanningServiceImplTest {

    private final ProductionPlanningServiceImpl service = new ProductionPlanningServiceImpl();

    @Test
    void findMatchingLines_shouldHitExactModelFirst() {
        Map<String, List<ProductionPlanningServiceImpl.LineCapacity>> capByModel = new HashMap<>();
        capByModel.put("6200VV*XC2", Collections.singletonList(
                ProductionPlanningServiceImpl.LineCapacity.of(8L, "L8", "6200VV*XC2", new BigDecimal("60"), 5)
        ));
        capByModel.put("6200", Collections.singletonList(
                ProductionPlanningServiceImpl.LineCapacity.of(9L, "L9", "6200", new BigDecimal("200"), 1)
        ));

        List<Long> sortedLineIds = service.findMatchingLines("6200VV*XC2", capByModel)
                .stream().map(ProductionPlanningServiceImpl.LineCapacity::getLineId).collect(Collectors.toList());

        assertEquals(Collections.singletonList(8L), sortedLineIds);
    }

    @Test
    void findMatchingLines_shouldFallbackToSeriesPrefixMatch() {
        Map<String, List<ProductionPlanningServiceImpl.LineCapacity>> capByModel = new HashMap<>();
        capByModel.put("6200", Collections.singletonList(
                ProductionPlanningServiceImpl.LineCapacity.of(2L, "L2", "6200", new BigDecimal("120"), 2)
        ));

        List<Long> sortedLineIds = service.findMatchingLines("6200VV*XC2", capByModel)
                .stream().map(ProductionPlanningServiceImpl.LineCapacity::getLineId).collect(Collectors.toList());

        assertEquals(Collections.singletonList(2L), sortedLineIds);
    }

    @Test
    void findMatchingLines_shouldReturnEmptyWhenNoMatch() {
        Map<String, List<ProductionPlanningServiceImpl.LineCapacity>> capByModel = new HashMap<>();
        capByModel.put("6300", Collections.singletonList(
                ProductionPlanningServiceImpl.LineCapacity.of(3L, "L3", "6300", new BigDecimal("80"), 1)
        ));

        List<ProductionPlanningServiceImpl.LineCapacity> lines = service.findMatchingLines("6200VV*XC2", capByModel);

        assertEquals(0, lines.size());
    }

    @Test
    void findMatchingLines_shouldSortByPriorityThenLineIdWhenMultipleSeriesMatched() {
        Map<String, List<ProductionPlanningServiceImpl.LineCapacity>> capByModel = new HashMap<>();
        capByModel.put("62", Collections.singletonList(
                ProductionPlanningServiceImpl.LineCapacity.of(1L, "L1", "62", new BigDecimal("100"), 5)
        ));
        capByModel.put("6200", Arrays.asList(
                ProductionPlanningServiceImpl.LineCapacity.of(3L, "L3", "6200", new BigDecimal("90"), 1),
                ProductionPlanningServiceImpl.LineCapacity.of(2L, "L2", "6200", new BigDecimal("110"), 1)
        ));

        List<Long> sortedLineIds = service.findMatchingLines("6200VV*XC2", capByModel)
                .stream().map(ProductionPlanningServiceImpl.LineCapacity::getLineId).collect(Collectors.toList());

        assertEquals(Arrays.asList(2L, 3L, 1L), sortedLineIds);
    }
}
