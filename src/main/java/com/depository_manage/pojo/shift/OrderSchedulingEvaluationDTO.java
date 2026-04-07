package com.depository_manage.pojo.shift;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrderSchedulingEvaluationDTO {

    /**
     * FREE_OK / PREEMPT_REQUIRED / DELAY_REQUIRED
     */
    private Stage stage = Stage.FREE_OK;

    @JsonProperty("free_capacity_qty_before_due")
    private Integer freeCapacityQtyBeforeDue = 0;

    @JsonProperty("required_preempt_line_count")
    private Integer requiredPreemptLineCount = 0;

    @JsonProperty("preempt_candidates")
    private List<PreemptCandidateDTO> preemptCandidates = new ArrayList<>();

    @JsonProperty("line_free_capacities")
    private List<LineFreeCapacityDTO> lineFreeCapacities = new ArrayList<>();

    @JsonProperty("allocation_suggestions")
    private List<AllocationSuggestionDTO> allocationSuggestions = new ArrayList<>();

    @JsonProperty("delay_days")
    private Integer delayDays = 0;

    @JsonProperty("predicted_finish_time")
    private String predictedFinishTime;

    @JsonProperty("recommended_lines")
    private List<DelayRecommendationLineDTO> recommendedLines = new ArrayList<>();

    public enum Stage {
        FREE_OK,
        PREEMPT_REQUIRED,
        DELAY_REQUIRED
    }

    @Data
    public static class PreemptCandidateDTO {
        private Long lineId;
        private String lineName;
        private Long planItemId;
        private String model;
        private Integer assignQty;
        private Integer orderDemandQty;

        @JsonProperty("estimated_output")
        private Integer estimatedOutput = 0;

        @JsonProperty("releasable_capacity_qty")
        private Integer releasableCapacityQty = 0;

        @JsonProperty("impact_delay_minutes")
        private Integer impactDelayMinutes = 0;
    }

    @Data
    public static class LineFreeCapacityDTO {
        private Long lineId;
        private String lineName;
        private Integer priority;

        @JsonProperty("capacity_per_hour")
        private BigDecimal capacityPerHour;

        @JsonProperty("total_window_minutes")
        private Integer totalWindowMinutes;

        @JsonProperty("occupied_minutes")
        private Integer occupiedMinutes;

        @JsonProperty("free_minutes")
        private Integer freeMinutes;

        @JsonProperty("free_qty_before_due")
        private Integer freeQtyBeforeDue;
    }

    @Data
    public static class AllocationSuggestionDTO {
        private Long lineId;
        private String lineName;

        @JsonProperty("allocated_qty")
        private Integer allocatedQty;

        @JsonProperty("estimated_finish_time")
        private String estimatedFinishTime;
    }

    @Data
    public static class DelayRecommendationLineDTO {
        private Long lineId;
        private String lineName;

        @JsonProperty("earliest_start_time")
        private String earliestStartTime;

        @JsonProperty("earliest_finish_time")
        private String earliestFinishTime;

        @JsonProperty("recommended_qty")
        private Integer recommendedQty;
    }
}
