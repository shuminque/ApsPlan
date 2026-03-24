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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/shift")
public class ShiftCalendarController {

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
        PlanPreviewResponseDTO preview = productionPlanningService.generatePlanPreview(
                request.getStart(),
                request.getEnd(),
                request.getPlanMode(),
                request.getInsertOrderIds(),
                request.getLineScope(),
                request.getLineIds(),
                request.getFreezeHours());
        PREVIEW_CACHE.put(session.getId(), preview.getEvents());
        return preview;
    }

    @PostMapping("/plan/commit")
    public int commitPlan(@RequestBody(required = false) List<CalendarEventDTO> selectedEvents, HttpSession session) {
        List<CalendarEventDTO> toCommit = selectedEvents;
        if (toCommit == null || toCommit.isEmpty()) {
            toCommit = PREVIEW_CACHE.getOrDefault(session.getId(), new ArrayList<>());
        }
        int committed = productionPlanService.commitPlan(toCommit);
        PREVIEW_CACHE.remove(session.getId());
        return committed;
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

    public static class PlanRangeRequest {
        private String start;
        private String end;
        private String planMode;
        private List<Long> insertOrderIds;
        private String lineScope;
        private List<Long> lineIds;
        private Integer freezeHours;

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

        public String getPlanMode() {
            return planMode;
        }

        public void setPlanMode(String planMode) {
            this.planMode = planMode;
        }

        public List<Long> getInsertOrderIds() {
            return insertOrderIds;
        }

        public void setInsertOrderIds(List<Long> insertOrderIds) {
            this.insertOrderIds = insertOrderIds;
        }

        public String getLineScope() {
            return lineScope;
        }

        public void setLineScope(String lineScope) {
            this.lineScope = lineScope;
        }

        public List<Long> getLineIds() {
            return lineIds;
        }

        public void setLineIds(List<Long> lineIds) {
            this.lineIds = lineIds;
        }

        public Integer getFreezeHours() {
            return freezeHours;
        }

        public void setFreezeHours(Integer freezeHours) {
            this.freezeHours = freezeHours;
        }
    }
}
