package com.depository_manage.controller.aps;

import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.exception.MyException;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.aps.ProductionPlanningService;
import com.depository_manage.service.aps.ProductionPlanService;
import com.depository_manage.service.aps.ShiftCalendarService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
                request.getFreezeHours(),
                request.parseOrderStartTimes());
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
        private Map<String, String> orderStartTimes;

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

        public Map<String, String> getOrderStartTimes() {
            return orderStartTimes;
        }

        public void setOrderStartTimes(Map<String, String> orderStartTimes) {
            this.orderStartTimes = orderStartTimes;
        }

        public Map<Long, LocalDateTime> parseOrderStartTimes() {
            if (orderStartTimes == null || orderStartTimes.isEmpty()) {
                return new ConcurrentHashMap<>();
            }
            Map<Long, LocalDateTime> parsed = new ConcurrentHashMap<>();
            for (Map.Entry<String, String> entry : orderStartTimes.entrySet()) {
                String orderIdRaw = entry.getKey();
                String startRaw = entry.getValue();
                if (orderIdRaw == null || orderIdRaw.trim().isEmpty()) {
                    throw new MyException(400, "orderStartTimes 包含空的订单ID");
                }
                if (startRaw == null || startRaw.trim().isEmpty()) {
                    throw new MyException(400, "订单 " + orderIdRaw + " 的开始时间不能为空");
                }
                Long orderId;
                try {
                    orderId = Long.parseLong(orderIdRaw.trim());
                } catch (NumberFormatException ex) {
                    throw new MyException(400, "orderStartTimes 的订单ID必须是数字，错误值: " + orderIdRaw);
                }
                parsed.put(orderId, parseDateTime(startRaw.trim(), orderIdRaw));
            }
            return parsed;
        }

        private LocalDateTime parseDateTime(String value, String orderId) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter minuteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            DateTimeFormatter secondFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            try {
                if (value.length() == 10) {
                    return LocalDate.parse(value, dateFormatter).atStartOfDay();
                }
                if (value.length() == 16) {
                    return LocalDateTime.parse(value, minuteFormatter);
                }
                if (value.length() == 19) {
                    return LocalDateTime.parse(value, secondFormatter);
                }
            } catch (DateTimeParseException ex) {
                throw new MyException(400, "订单 " + orderId + " 的开始时间格式错误: " + value
                        + "，仅支持 yyyy-MM-dd / yyyy-MM-ddTHH:mm / yyyy-MM-ddTHH:mm:ss");
            }
            throw new MyException(400, "订单 " + orderId + " 的开始时间格式错误: " + value
                    + "，仅支持 yyyy-MM-dd / yyyy-MM-ddTHH:mm / yyyy-MM-ddTHH:mm:ss");
        }
    }
}
