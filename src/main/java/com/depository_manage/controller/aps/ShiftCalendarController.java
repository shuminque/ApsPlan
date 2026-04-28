package com.depository_manage.controller.aps;

import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.exception.MyException;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.OrderSchedulingEvaluationDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.aps.ProductionPlanningService;
import com.depository_manage.service.aps.OrderSchedulingEvaluationService;
import com.depository_manage.service.aps.ProductionPlanService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.service.aps.planning.PlanningRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/shift")
public class ShiftCalendarController {

    private static final Logger log = LoggerFactory.getLogger(ShiftCalendarController.class);
    private static final Map<String, PreviewSessionState> PREVIEW_CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private ShiftCalendarService shiftCalendarService;
    @Resource
    private ProductionPlanningService productionPlanningService;
    @Resource
    private ProductionPlanService productionPlanService;
    @Resource
    private OrderSchedulingEvaluationService orderSchedulingEvaluationService;

    // 获取日历事件
    @GetMapping("/events")
    public List<CalendarEventDTO> getEvents(@RequestParam String start, @RequestParam String end) {
        return shiftCalendarService.getCalendarEvents(start, end);
    }

    @PostMapping("/plan/preview")
    public PlanPreviewResponseDTO previewPlan(@RequestBody PlanRangeRequest request, HttpSession session) {
        PlanPreviewResponseDTO preview = productionPlanningService.generatePlanPreview(new PlanningRequest(
                request.getStart(),
                request.getEnd(),
                request.getPlanMode(),
                request.getInsertOrderIds(),
                request.getLineScope(),
                request.getLineIds(),
                request.getFreezeHours(),
                request.getOrderStartTimes(),
                request.getObjective(),
                request.getSelectedInsertLineIds()));
        preview.setInsertSuggestion(normalizeInsertSuggestion(preview.getInsertSuggestion()));
        PREVIEW_CACHE.put(session.getId(), new PreviewSessionState(
                preview.getEvents(),
                preview.getInsertSuggestion(),
                LocalDateTime.now(),
                resolveOperator(session)));
        return preview;
    }

    @GetMapping("/plan/preview/insert-suggestion")
    public PlanPreviewResponseDTO.InsertSuggestionDTO getInsertSuggestion(HttpSession session) {
        PreviewSessionState previewState = PREVIEW_CACHE.get(session.getId());
        if (previewState == null) {
            throw new MyException("请先执行预览后再查询插队候选线");
        }
        return normalizeInsertSuggestion(previewState.insertSuggestion);
    }

    private PlanPreviewResponseDTO.InsertSuggestionDTO normalizeInsertSuggestion(PlanPreviewResponseDTO.InsertSuggestionDTO suggestion) {
        if (suggestion == null) {
            return new PlanPreviewResponseDTO.InsertSuggestionDTO();
        }
        if (suggestion.getCandidateLines() == null) {
            suggestion.setCandidateLines(new ArrayList<>());
        }
        return suggestion;
    }

    /**
     * 订单排产评估接口，当前先固定返回结构，后续算法直接填充该结构。
     */
    @GetMapping("/plan/evaluateOrderScheduling")
    public OrderSchedulingEvaluationDTO evaluateOrderScheduling(@RequestParam String model,
                                                                @RequestParam(required = false) String craft,
                                                                @RequestParam Integer quantity,
                                                                @RequestParam String deliveryDate) {
        if (!org.springframework.util.StringUtils.hasText(model)) {
            throw new MyException("model 不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new MyException("quantity 必须大于 0");
        }
        if (!org.springframework.util.StringUtils.hasText(deliveryDate)) {
            throw new MyException("deliveryDate 不能为空");
        }
        java.time.LocalDate dueDate;
        try {
            dueDate = java.time.LocalDate.parse(deliveryDate);
        } catch (Exception ex) {
            throw new MyException("deliveryDate 格式错误，要求 yyyy-MM-dd");
        }
        return orderSchedulingEvaluationService.evaluate(model, craft, quantity, dueDate);
    }

    @PostMapping("/plan/commit")
    public int commitPlan(@RequestBody(required = false) Object requestBody, HttpSession session) {
        CommitPlanRequest request = toCommitPlanRequest(requestBody);
        List<CalendarEventDTO> toCommit = request.getSelectedEvents();
        PreviewSessionState previewState = PREVIEW_CACHE.get(session.getId());
        if (toCommit == null || toCommit.isEmpty()) {
            toCommit = previewState == null ? new ArrayList<>() : previewState.events;
        }
        validateSelectedInsertLines(request.getSelectedInsertLineIds(), previewState);
        if (previewState != null && request.getSelectedInsertLineIds() != null && !request.getSelectedInsertLineIds().isEmpty()) {
            log.info("insert-line-selection audit operator={}, selectedInsertLineIds={}, selectedAt={}, previewGeneratedAt={}, previewGeneratedBy={}",
                    resolveOperator(session),
                    request.getSelectedInsertLineIds(),
                    LocalDateTime.now(),
                    previewState.generatedAt,
                    previewState.generatedBy);
        }
        Set<Long> selectedInsertLineIdSet = request.getSelectedInsertLineIds() == null
                ? Collections.emptySet()
                : request.getSelectedInsertLineIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        int committed = productionPlanService.commitPlan(toCommit, selectedInsertLineIdSet);
        PREVIEW_CACHE.remove(session.getId());
        return committed;
    }

    @DeleteMapping("/plan/preview")
    public void clearPreview(HttpSession session) {
        PREVIEW_CACHE.remove(session.getId());
    }

    private void validateSelectedInsertLines(List<Long> selectedInsertLineIds, PreviewSessionState previewState) {
        if (previewState == null) {
            return;
        }
        PlanPreviewResponseDTO.InsertSuggestionDTO suggestion = previewState.insertSuggestion;
        if (suggestion == null || suggestion.getCandidateLines() == null || suggestion.getCandidateLines().isEmpty()) {
            return;
        }
        Set<Long> candidateIds = suggestion.getCandidateLines().stream()
                .map(PlanPreviewResponseDTO.CandidateLineDTO::getLineId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> selectedIds = selectedInsertLineIds == null ? Collections.emptyList() : selectedInsertLineIds;
        if (!candidateIds.containsAll(selectedIds)) {
            throw new MyException("selectedInsertLineIds 存在非候选产线，请刷新预览后重试");
        }
        int requiredCount = suggestion.getRequiredInsertLineCount() == null ? 0 : suggestion.getRequiredInsertLineCount();
        if (requiredCount <= 0 || selectedIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueSelected = new HashSet<>(selectedIds);
        if (uniqueSelected.size() < requiredCount) {
            throw new MyException("已选插队线数量不足，系统建议最少选择 " + requiredCount + " 条产线");
        }
    }

    private CommitPlanRequest toCommitPlanRequest(Object requestBody) {
        if (requestBody == null) {
            return new CommitPlanRequest();
        }
        if (requestBody instanceof List) {
            List<CalendarEventDTO> events = OBJECT_MAPPER.convertValue(requestBody, new TypeReference<List<CalendarEventDTO>>() {});
            CommitPlanRequest request = new CommitPlanRequest();
            request.setSelectedEvents(events);
            return request;
        }
        return OBJECT_MAPPER.convertValue(requestBody, CommitPlanRequest.class);
    }

    private String resolveOperator(HttpSession session) {
        Object user = session == null ? null : session.getAttribute("user");
        if (user == null && session != null) {
            user = session.getAttribute("username");
        }
        return user == null ? "session:" + (session == null ? "unknown" : session.getId()) : user.toString();
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
        private String objective;
        private List<Long> selectedInsertLineIds;

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

        public String getObjective() {
            return objective;
        }

        public void setObjective(String objective) {
            this.objective = objective;
        }

        public List<Long> getSelectedInsertLineIds() {
            return selectedInsertLineIds;
        }

        public void setSelectedInsertLineIds(List<Long> selectedInsertLineIds) {
            this.selectedInsertLineIds = selectedInsertLineIds;
        }

    }

    public static class CommitPlanRequest {
        private List<CalendarEventDTO> selectedEvents;
        private List<Long> selectedInsertLineIds;

        public List<CalendarEventDTO> getSelectedEvents() {
            return selectedEvents;
        }

        public void setSelectedEvents(List<CalendarEventDTO> selectedEvents) {
            this.selectedEvents = selectedEvents;
        }

        public List<Long> getSelectedInsertLineIds() {
            return selectedInsertLineIds;
        }

        public void setSelectedInsertLineIds(List<Long> selectedInsertLineIds) {
            this.selectedInsertLineIds = selectedInsertLineIds;
        }
    }

    private static class PreviewSessionState {
        private final List<CalendarEventDTO> events;
        private final PlanPreviewResponseDTO.InsertSuggestionDTO insertSuggestion;
        private final LocalDateTime generatedAt;
        private final String generatedBy;

        private PreviewSessionState(List<CalendarEventDTO> events,
                                    PlanPreviewResponseDTO.InsertSuggestionDTO insertSuggestion,
                                    LocalDateTime generatedAt,
                                    String generatedBy) {
            this.events = events;
            this.insertSuggestion = insertSuggestion;
            this.generatedAt = generatedAt;
            this.generatedBy = generatedBy;
        }
    }
}
