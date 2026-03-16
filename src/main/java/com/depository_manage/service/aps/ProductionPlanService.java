package com.depository_manage.service.aps;

import com.depository_manage.pojo.shift.CalendarEventDTO;

import java.util.List;

public interface ProductionPlanService {
    int commitPlan(List<CalendarEventDTO> events);
}
