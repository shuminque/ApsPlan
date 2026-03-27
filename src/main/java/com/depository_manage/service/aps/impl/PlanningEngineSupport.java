package com.depository_manage.service.aps.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

final class PlanningEngineSupport {

    private PlanningEngineSupport() {
    }

    static PlanningResult emptyResult() {
        return new PlanningResult(java.util.Collections.emptyList(), java.util.Collections.emptyList(), null, null,
                new PlanningResult.Metrics(0, 0, BigDecimal.ZERO), null);
    }

    static PlanningResult.Metrics calculateMetrics(List<DemandItem> demands, LocalDate endExclusive) {
        int squeezedOrderCount = 0;
        int delayedDays = 0;
        int insertRequired = 0;
        int insertPlanned = 0;

        for (DemandItem demand : demands) {
            if (demand.lockedInsert()) {
                insertRequired += demand.required();
                insertPlanned += demand.plannedQuantity();
                if (demand.plannedQuantity() < demand.required()) {
                    squeezedOrderCount++;
                }
            }
            LocalDate delivery = demand.earliestDeliveryDate();
            if (delivery != null && demand.remaining() > 0 && endExclusive != null) {
                LocalDate baseline = delivery.plusDays(1);
                if (endExclusive.isAfter(baseline)) {
                    delayedDays += (int) ChronoUnit.DAYS.between(baseline, endExclusive);
                }
            }
        }

        BigDecimal insertRate = insertRequired <= 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(insertPlanned)
                .divide(BigDecimal.valueOf(insertRequired), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        return new PlanningResult.Metrics(squeezedOrderCount, delayedDays, insertRate);
    }
}
