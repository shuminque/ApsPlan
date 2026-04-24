package com.depository_manage.service.aps.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public class NormalizedPlanningRequest {

    private final LocalDate requestStart;
    private final LocalDate start;
    private final LocalDate endExclusive;
    private final LocalDateTime requestStartAt;
    private final LocalDateTime effectiveStartAt;
    private final String mode;
    private final String objective;
    private final Set<Long> insertOrderIds;
    private final Set<Long> scopedLineIds;
    private final int freezeWindowHours;
    private final Map<Long, LocalDateTime> orderStartTimes;
    private final Set<Long> selectedInsertLineIds;

    public NormalizedPlanningRequest(LocalDate requestStart,
                                     LocalDate start,
                                     LocalDate endExclusive,
                                     LocalDateTime requestStartAt,
                                     LocalDateTime effectiveStartAt,
                                     String mode,
                                     String objective,
                                     Set<Long> insertOrderIds,
                                     Set<Long> scopedLineIds,
                                     int freezeWindowHours,
                                     Map<Long, LocalDateTime> orderStartTimes,
                                     Set<Long> selectedInsertLineIds) {
        this.requestStart = requestStart;
        this.start = start;
        this.endExclusive = endExclusive;
        this.requestStartAt = requestStartAt;
        this.effectiveStartAt = effectiveStartAt;
        this.mode = mode;
        this.objective = objective;
        this.insertOrderIds = insertOrderIds;
        this.scopedLineIds = scopedLineIds;
        this.freezeWindowHours = freezeWindowHours;
        this.orderStartTimes = orderStartTimes;
        this.selectedInsertLineIds = selectedInsertLineIds;
    }

    public LocalDate getRequestStart() {
        return requestStart;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEndExclusive() {
        return endExclusive;
    }

    public LocalDateTime getRequestStartAt() {
        return requestStartAt;
    }

    public LocalDateTime getEffectiveStartAt() {
        return effectiveStartAt;
    }

    public String getMode() {
        return mode;
    }

    public String getObjective() {
        return objective;
    }

    public Set<Long> getInsertOrderIds() {
        return insertOrderIds;
    }

    public Set<Long> getScopedLineIds() {
        return scopedLineIds;
    }

    public int getFreezeWindowHours() {
        return freezeWindowHours;
    }

    public Map<Long, LocalDateTime> getOrderStartTimes() {
        return orderStartTimes;
    }

    public Set<Long> getSelectedInsertLineIds() {
        return selectedInsertLineIds;
    }

    public boolean isValid() {
        return requestStart != null && endExclusive != null;
    }
}
