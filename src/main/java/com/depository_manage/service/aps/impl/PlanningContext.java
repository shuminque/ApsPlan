package com.depository_manage.service.aps.impl;

import com.depository_manage.service.aps.planning.NormalizedPlanningRequest;

import java.time.Clock;
import java.time.ZoneId;

public class PlanningContext {

    private final NormalizedPlanningRequest normalizedRequest;
    private final PlanningSnapshot snapshot;
    private final Clock clock;
    private final ZoneId zoneId;

    public PlanningContext(NormalizedPlanningRequest normalizedRequest,
                           PlanningSnapshot snapshot,
                           Clock clock,
                           ZoneId zoneId) {
        this.normalizedRequest = normalizedRequest;
        this.snapshot = snapshot;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    public NormalizedPlanningRequest getNormalizedRequest() {
        return normalizedRequest;
    }

    public PlanningSnapshot getSnapshot() {
        return snapshot;
    }

    public Clock getClock() {
        return clock;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}
