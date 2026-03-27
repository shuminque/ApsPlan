package com.depository_manage.service.aps.impl;

import java.time.LocalDateTime;
import java.util.List;

public class PlanningResult {
    private final List<PlanSlice> slices;
    private final List<DemandItem> demands;
    private final LocalDateTime actualStart;
    private final LocalDateTime actualEnd;
    private final Metrics metrics;
    private final Object diagnostics;

    public PlanningResult(List<PlanSlice> slices,
                          List<DemandItem> demands,
                          LocalDateTime actualStart,
                          LocalDateTime actualEnd,
                          Metrics metrics,
                          Object diagnostics) {
        this.slices = slices;
        this.demands = demands;
        this.actualStart = actualStart;
        this.actualEnd = actualEnd;
        this.metrics = metrics;
        this.diagnostics = diagnostics;
    }

    public List<PlanSlice> getSlices() { return slices; }
    public List<DemandItem> getDemands() { return demands; }
    public LocalDateTime getActualStart() { return actualStart; }
    public LocalDateTime getActualEnd() { return actualEnd; }
    public Metrics getMetrics() { return metrics; }
    public Object getDiagnostics() { return diagnostics; }

    public static class Metrics {
        private final int squeezedOrderCount;
        private final int delayedDays;
        private final java.math.BigDecimal insertFulfillmentRate;

        public Metrics(int squeezedOrderCount, int delayedDays, java.math.BigDecimal insertFulfillmentRate) {
            this.squeezedOrderCount = squeezedOrderCount;
            this.delayedDays = delayedDays;
            this.insertFulfillmentRate = insertFulfillmentRate;
        }

        public int getSqueezedOrderCount() { return squeezedOrderCount; }
        public int getDelayedDays() { return delayedDays; }
        public java.math.BigDecimal getInsertFulfillmentRate() { return insertFulfillmentRate; }
    }
}
