package com.depository_manage.controller.aps;

import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.service.aps.ShiftCalendarService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/shift")
public class ShiftCalendarController {

    @Resource
    private ShiftCalendarService shiftCalendarService;

    // 获取日历事件
    @GetMapping("/events")
    public List<CalendarEventDTO> getEvents(@RequestParam String start, @RequestParam String end) {
        return shiftCalendarService.getCalendarEvents(start, end);
    }

    // 添加排班
    @PostMapping("/add")
    public int addSchedule(@RequestBody ShiftSchedule schedule) {
        return shiftCalendarService.addSchedule(schedule);
    }

    // 更新排班
    @PutMapping("/update")
    public int updateSchedule(@RequestBody ShiftSchedule schedule) {
        return shiftCalendarService.updateSchedule(schedule);
    }

    // 删除排班
    @DeleteMapping("/delete/{id}")
    public int deleteSchedule(@PathVariable("id") Long id) {
        return shiftCalendarService.deleteSchedule(id);
    }
    @GetMapping("/day")
    public List<ShiftSchedule> getSchedulesByDay(
            @RequestParam String date // yyyy-MM-dd
    ) {
        return shiftCalendarService.getSchedulesByDate(date);
    }
    @GetMapping("/day/team")
    public List<ShiftSchedule> getSchedulesByDayAndTeam(
            @RequestParam String date,
            @RequestParam Long teamId
    ) {
        return shiftCalendarService.getSchedulesByDateAndTeam(date, teamId);
    }

    @PostMapping("/day/save")
    public void saveDay(@RequestBody List<ShiftSchedule> list) {
        System.out.println(list);
        shiftCalendarService.saveDaySchedules(list);
    }

}
