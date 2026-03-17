package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLineRuntime;
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
    void normalizeModel_shouldExtractSeriesPrefix() {
        assertEquals("6200", ProductionPlanningServiceImpl.normalizeModel("6200VV*XC2"));
    }

    @Test
    void selectCandidateLines_shouldPreferHighPriorityScoreWhenMultipleLinesAvailable() {
        Map<String, List<ProductionPlanningServiceImpl.LineCapacity>> capByModel = new HashMap<>();
        capByModel.put("6200", Arrays.asList(
                ProductionPlanningServiceImpl.LineCapacity.of(1L, "L1", "6200", new BigDecimal("120"), 30, 90, 0),
                ProductionPlanningServiceImpl.LineCapacity.of(2L, "L2", "6200", new BigDecimal("100"), 10, 30, 10)
        ));

        Map<Long, ProductionLineRuntime> runtimeByLine = new HashMap<>();
        runtimeByLine.put(1L, runtime(1L, 0, "6000"));
        runtimeByLine.put(2L, runtime(2L, 1, "6200"));

        List<Long> sortedLineIds = service.selectCandidateLines("6200", capByModel, runtimeByLine)
                .stream().map(ProductionPlanningServiceImpl.LineCapacity::getLineId).collect(Collectors.toList());

        assertEquals(Arrays.asList(2L, 1L), sortedLineIds);
    }

    @Test
    void selectCandidateLines_shouldPreferExactMatchOverSeriesMatch() {
        Map<String, List<ProductionPlanningServiceImpl.LineCapacity>> capByModel = new HashMap<>();
        capByModel.put("6200VV*XC2", Collections.singletonList(
                ProductionPlanningServiceImpl.LineCapacity.of(8L, "L8", "6200VV*XC2", new BigDecimal("60"), 5, 5, 0)
        ));
        capByModel.put("6200", Collections.singletonList(
                ProductionPlanningServiceImpl.LineCapacity.of(9L, "L9", "6200", new BigDecimal("200"), 5, 5, 99)
        ));

        List<Long> sortedLineIds = service.selectCandidateLines("6200VV*XC2", capByModel, Collections.emptyMap())
                .stream().map(ProductionPlanningServiceImpl.LineCapacity::getLineId).collect(Collectors.toList());

        assertEquals(Collections.singletonList(8L), sortedLineIds);
    }

    private static ProductionLineRuntime runtime(Long lineId, Integer status, String currentModel) {
        ProductionLineRuntime runtime = new ProductionLineRuntime();
        runtime.setLineId(lineId);
        runtime.setStatus(status);
        runtime.setCurrentModel(currentModel);
        return runtime;
    }
}
