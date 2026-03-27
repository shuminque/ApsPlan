package com.depository_manage.service.aps.impl;

import java.time.LocalDate;

public interface PlannableDemand {
    int remaining();

    void applyPlan(int assigned, LocalDate planDay);

    LocalDate earliestStartDate();

    default boolean canScheduleOn(LocalDate day) {
        LocalDate earliest = earliestStartDate();
        return earliest == null || !day.isBefore(earliest);
    }
}
