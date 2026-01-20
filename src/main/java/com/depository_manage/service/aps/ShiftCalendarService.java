package com.depository_manage.service.aps;

import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.entity.aps.ShiftSchedule;

import java.util.List;

public interface ShiftCalendarService {

    List<CalendarEventDTO> getCalendarEvents(String startDate, String endDate);

    int addSchedule(ShiftSchedule schedule);

    int updateSchedule(ShiftSchedule schedule);

    int deleteSchedule(Long scheduleId);

    List<ShiftSchedule> getSchedulesByDate(String date);
    // 按日期 + 班组查询
    List<ShiftSchedule> getSchedulesByDateAndTeam(String date, Long teamId);

    public void saveDaySchedules(List<ShiftSchedule> list);

}
