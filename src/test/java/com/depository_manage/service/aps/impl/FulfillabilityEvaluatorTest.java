package com.depository_manage.service.aps.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FulfillabilityEvaluatorTest {

    private final FulfillabilityEvaluator evaluator = new FulfillabilityEvaluator();

    @Test
    void shouldPassWhenIdleCapacityCanCoverDemand() {
        List<DemandItem> demands = Collections.singletonList(newDemand(120, false, LocalDate.now().plusDays(1)));
        Map<String, List<LineCapacity>> lineCapByModel = new HashMap<String, List<LineCapacity>>();
        lineCapByModel.put("6205", Collections.singletonList(
                LineCapacity.of(1L, "L1", "6205", new BigDecimal("15"), 1, "车加工")
        ));
        Map<Long, PlanningSnapshot.LineRuntimeView> runtime = Collections.singletonMap(1L,
                new PlanningSnapshot.LineRuntimeView(0, null, new BigDecimal("15"), null, null));

        FulfillabilityAssessment assessment = evaluator.evaluate(
                demands,
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                shiftHoursMap(new BigDecimal("8"), 2),
                lineCapByModel,
                runtime
        );

        assertTrue(assessment.isCanFulfillByIdleLines());
        assertEquals(240, assessment.getIdleCapacityBeforeDeadline());
        assertEquals(0, assessment.getRequiredInsertQuantity());
        assertEquals(0, assessment.getRequiredInsertLineCount());
    }

    @Test
    void shouldEstimateInsertLinesWhenIdleCapacityInsufficient() {
        List<DemandItem> demands = Collections.singletonList(newDemand(200, true, LocalDate.now().plusDays(1)));
        Map<String, List<LineCapacity>> lineCapByModel = new HashMap<String, List<LineCapacity>>();
        lineCapByModel.put("6205", Arrays.asList(
                LineCapacity.of(1L, "idle", "6205", new BigDecimal("10"), 1, "车加工"),
                LineCapacity.of(2L, "run-1", "6205", new BigDecimal("12"), 1, "车加工"),
                LineCapacity.of(3L, "run-2", "6205", new BigDecimal("8"), 1, "车加工")
        ));
        Map<Long, PlanningSnapshot.LineRuntimeView> runtime = new HashMap<Long, PlanningSnapshot.LineRuntimeView>();
        runtime.put(1L, new PlanningSnapshot.LineRuntimeView(0, null, new BigDecimal("10"), null, null));
        runtime.put(2L, new PlanningSnapshot.LineRuntimeView(1, null, new BigDecimal("12"), null, null));
        runtime.put(3L, new PlanningSnapshot.LineRuntimeView(1, null, new BigDecimal("8"), null, null));

        FulfillabilityAssessment assessment = evaluator.evaluate(
                demands,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                shiftHoursMap(new BigDecimal("8"), 2),
                lineCapByModel,
                runtime
        );

        assertFalse(assessment.isCanFulfillByIdleLines());
        assertEquals(160, assessment.getIdleCapacityBeforeDeadline());
        assertEquals(40, assessment.getRequiredInsertQuantity());
        assertEquals(1, assessment.getRequiredInsertLineCount());
    }

    private Map<LocalDate, BigDecimal> shiftHoursMap(BigDecimal hours, int days) {
        Map<LocalDate, BigDecimal> map = new HashMap<LocalDate, BigDecimal>();
        for (int i = 0; i < days; i++) {
            map.put(LocalDate.now().plusDays(i), hours);
        }
        return map;
    }

    private DemandItem newDemand(int required, boolean insert, LocalDate dueDate) {
        return new DemandItem(1L, "C1", "LA", "6205", "车加工", required,
                0, 1, required, 0,
                Date.from(dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                1, insert, LocalDateTime.now(), LocalDate.now(), ZoneId.systemDefault());
    }
}
