package com.depository_manage.controller.aps;

import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.aps.ProductionPlanningService;
import com.depository_manage.service.aps.ProductionPlanService;
import com.depository_manage.service.aps.ShiftCalendarService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/shift")
public class ShiftCalendarController {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Map<String, List<CalendarEventDTO>> PREVIEW_CACHE = new ConcurrentHashMap<>();

    @Resource
    private ShiftCalendarService shiftCalendarService;
    @Resource
    private ProductionPlanningService productionPlanningService;
    @Resource
    private ProductionPlanService productionPlanService;

    // 获取日历事件
    @GetMapping("/events")
    public List<CalendarEventDTO> getEvents(@RequestParam String start, @RequestParam String end) {
        return shiftCalendarService.getCalendarEvents(start, end);
    }

    @PostMapping("/plan/preview")
    public PlanPreviewResponseDTO previewPlan(@RequestBody PlanRangeRequest request, HttpSession session) {
        PlanPreviewResponseDTO preview = productionPlanningService.generatePlanPreview(request.getStart(), request.getEnd());
        PREVIEW_CACHE.put(session.getId(), preview.getEvents());
        return preview;
    }

    @PostMapping("/plan/commit")
    public int commitPlan(@RequestBody(required = false) List<CalendarEventDTO> selectedEvents, HttpSession session) {
        List<CalendarEventDTO> toCommit = selectedEvents;
        if (toCommit == null || toCommit.isEmpty()) {
            toCommit = PREVIEW_CACHE.getOrDefault(session.getId(), new ArrayList<>());
        }
        int inserted = 0;
        for (CalendarEventDTO event : toCommit) {
            ShiftSchedule schedule = toShiftSchedule(event);
            if (schedule == null) {
                continue;
            }
            inserted += shiftCalendarService.addSchedule(schedule);
        }
        productionPlanService.commitPlan(toCommit);
        PREVIEW_CACHE.remove(session.getId());
        return inserted;
    }

    @DeleteMapping("/plan/preview")
    public void clearPreview(HttpSession session) {
        PREVIEW_CACHE.remove(session.getId());
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
    public List<ShiftSchedule> getSchedulesByDay(@RequestParam String date) {
        return shiftCalendarService.getSchedulesByDate(date);
    }

    @GetMapping("/day/team")
    public List<ShiftSchedule> getSchedulesByDayAndTeam(@RequestParam String date, @RequestParam Long teamId) {
        return shiftCalendarService.getSchedulesByDateAndTeam(date, teamId);
    }

    @PostMapping("/day/save")
    public void saveDay(@RequestBody List<ShiftSchedule> list) {
        shiftCalendarService.saveDaySchedules(list);
    }

    private ShiftSchedule toShiftSchedule(CalendarEventDTO event) {
        if (event == null || event.getStart() == null || event.getEnd() == null) {
            return null;
        }
        LocalDateTime start = LocalDateTime.parse(event.getStart(), DATE_TIME_FMT);
        LocalDateTime end = LocalDateTime.parse(event.getEnd(), DATE_TIME_FMT);
        ShiftSchedule schedule = new ShiftSchedule();
        schedule.setTeamID(trimPlanPrefix(event.getTitle()));
        schedule.setScheduleDate(Date.from(start.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        schedule.setStartDateTime(Date.from(start.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setEndDateTime(Date.from(end.atZone(ZoneId.systemDefault()).toInstant()));
        return schedule;
    }

    private String trimPlanPrefix(String title) {
        if (title == null) {
            return "自动排产";
        }
        String trimmed = title.trim();
        if (trimmed.startsWith("[排产]")) {
            trimmed = trimmed.substring("[排产]".length()).trim();
        }
        return trimmed.isEmpty() ? "自动排产" : trimmed;
    }

    public static class PlanRangeRequest {
        private String start;
        private String end;

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }
    }
}
