package com.depository_manage.pojo.shift;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlanPreviewResponseDTO {
    private List<CalendarEventDTO> events = new ArrayList<>();
    private List<PlanPreviewOrderDTO> orders = new ArrayList<>();
    private List<PlanPreviewDailyDTO> dailyOutputs = new ArrayList<>();
    private InsertSuggestionDTO insertSuggestion = new InsertSuggestionDTO();
    private String planStart;
    private String planEnd;
    private Integer squeezedOrderCount = 0;
    private Integer delayedDays = 0;
    private BigDecimal insertFulfillmentRate = BigDecimal.ZERO;
    private Boolean autoInsertTriggered = false;
    private Integer requiredInsertQuantity = 0;
    private Integer requiredInsertLineCount = 0;
    private String requiredInsertLineHint;
    private String insertDeadline;
    private Integer deadlineDaysFromPlanStart;
    private Integer deadlineDaysFromTodayServer;
    private String serverTimeZone;
    private Boolean insertDelayRequired = false;
    private Integer insertShortageQuantity = 0;
    private Integer suggestedDelayedDays;

    @Data
    public static class InsertSuggestionDTO {
        private Integer requiredInsertLineCount = 0;
        private String requiredInsertLineHint;
        private String diagnosticTag;
        private List<CandidateLineDTO> candidateLines = new ArrayList<>();
    }

    @Data
    public static class CandidateLineDTO {
        private Long lineId;
        private String lineName;
        private String currentModel;
        private Integer releasableCapacity = 0;
        private String riskTag;
        private BigDecimal baseCapacityPerHour = BigDecimal.ZERO;
        private BigDecimal runtimeCapacityPerHour = BigDecimal.ZERO;
        private BigDecimal effectiveCapacityPerHour = BigDecimal.ZERO;
        private BigDecimal totalShiftHours = BigDecimal.ZERO;
        private BigDecimal totalShiftHoursRaw = BigDecimal.ZERO;
        private BigDecimal dayCountRaw = BigDecimal.ZERO;
        private Integer capacityRaw = 0;
        private BigDecimal totalShiftHoursWithinDeadline = BigDecimal.ZERO;
        private BigDecimal dayCountWithinDeadline = BigDecimal.ZERO;
        private Integer capacityWithinDeadline = 0;
        private String windowStartDate;
        private String windowEndDate;
        private BigDecimal effectiveWindowDays = BigDecimal.ZERO;
        private String releasableCapacityFormula;
        private String releasableCapacityFormulaRaw;
        private String releasableCapacityFormulaWithinDeadline;
        private Boolean deadlineCheckPassed = true;
        private String delayRiskReason;
        private BigDecimal occupiedHoursBeforeDeadline = BigDecimal.ZERO;
    }
}
