package com.depository_manage.service.aps.planning;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PlanningRequest {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final String startDate;
    private final String endDate;
    private final String planMode;
    private final String lineScope;
    private final List<Long> lineIds;
    private final Integer freezeHours;
    private final Map<String, String> orderStartTimes;
    private final String objective;
    private final List<Long> selectedInsertLineIds;

    public PlanningRequest(String startDate,
                           String endDate,
                           String planMode,
                           String lineScope,
                           List<Long> lineIds,
                           Integer freezeHours,
                           Map<String, String> orderStartTimes,
                           String objective,
                           List<Long> selectedInsertLineIds) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.planMode = planMode;
        this.lineScope = lineScope;
        this.lineIds = lineIds;
        this.freezeHours = freezeHours;
        this.orderStartTimes = orderStartTimes;
        this.objective = objective;
        this.selectedInsertLineIds = selectedInsertLineIds;
    }

    public static PlanningRequest fromLegacyParameters(String startDate,
                                                       String endDate,
                                                       String planMode,
                                                       String lineScope,
                                                       List<Long> lineIds,
                                                       Integer freezeHours,
                                                       Map<Long, LocalDateTime> orderStartTimes,
                                                       String objective) {
        Map<String, String> serializedOrderStartTimes;
        if (orderStartTimes == null || orderStartTimes.isEmpty()) {
            serializedOrderStartTimes = Collections.emptyMap();
        } else {
            serializedOrderStartTimes = new java.util.HashMap<>();
            for (Map.Entry<Long, LocalDateTime> entry : orderStartTimes.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                serializedOrderStartTimes.put(String.valueOf(entry.getKey()), DATE_TIME_FMT.format(entry.getValue()));
            }
        }
        return new PlanningRequest(startDate, endDate, planMode, lineScope,
                lineIds, freezeHours, serializedOrderStartTimes, objective, Collections.emptyList());
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getPlanMode() {
        return planMode;
    }

    public String getLineScope() {
        return lineScope;
    }

    public List<Long> getLineIds() {
        return lineIds;
    }

    public Integer getFreezeHours() {
        return freezeHours;
    }

    public Map<String, String> getOrderStartTimes() {
        return orderStartTimes;
    }

    public String getObjective() {
        return objective;
    }

    public List<Long> getSelectedInsertLineIds() {
        return selectedInsertLineIds;
    }
}
