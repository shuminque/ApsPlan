package com.depository_manage.service.aps;

import com.depository_manage.pojo.shift.CalendarEventDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface ProductionPlanService {
    int commitPlan(List<CalendarEventDTO> events);

    default int commitPlan(List<CalendarEventDTO> events, Set<Long> selectedInsertLineIds) {
        return commitPlan(events);
    }

    int rollbackPlanWindow(LocalDateTime from, LocalDateTime to, Set<Long> lineIds);
}
