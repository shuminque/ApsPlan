package com.depository_manage.service.aps.impl;

public interface PlanningEngine {
    PlanningResult plan(PlanningContext context);

    String version();
}
