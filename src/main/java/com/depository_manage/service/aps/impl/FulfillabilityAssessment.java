package com.depository_manage.service.aps.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FulfillabilityAssessment {

    private final boolean canFulfillByIdleLines;
    private final int idleCapacityBeforeDeadline;
    private final int requiredInsertQuantity;
    private final int requiredInsertLineCount;
    private final String insertDeadline;
    private final String eligibleDemandModel;
    private final String eligibleDemandCraft;
    private final List<Long> eligibleLineIds;

    public FulfillabilityAssessment(boolean canFulfillByIdleLines,
                                    int idleCapacityBeforeDeadline,
                                    int requiredInsertQuantity,
                                    int requiredInsertLineCount,
                                    String insertDeadline,
                                    String eligibleDemandModel,
                                    String eligibleDemandCraft,
                                    List<Long> eligibleLineIds) {
        this.canFulfillByIdleLines = canFulfillByIdleLines;
        this.idleCapacityBeforeDeadline = idleCapacityBeforeDeadline;
        this.requiredInsertQuantity = requiredInsertQuantity;
        this.requiredInsertLineCount = requiredInsertLineCount;
        this.insertDeadline = insertDeadline;
        this.eligibleDemandModel = eligibleDemandModel;
        this.eligibleDemandCraft = eligibleDemandCraft;
        this.eligibleLineIds = eligibleLineIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<Long>(eligibleLineIds));
    }

    public boolean isCanFulfillByIdleLines() {
        return canFulfillByIdleLines;
    }

    public int getIdleCapacityBeforeDeadline() {
        return idleCapacityBeforeDeadline;
    }

    public int getRequiredInsertQuantity() {
        return requiredInsertQuantity;
    }

    public int getRequiredInsertLineCount() {
        return requiredInsertLineCount;
    }

    public String getInsertDeadline() {
        return insertDeadline;
    }

    public String getTriggerGapModel() {
        return eligibleDemandModel;
    }

    public String getTriggerGapCraft() {
        return eligibleDemandCraft;
    }

    public List<Long> getTriggerGapLineIds() {
        return eligibleLineIds;
    }

    public String getEligibleDemandModel() {
        return eligibleDemandModel;
    }

    public String getEligibleDemandCraft() {
        return eligibleDemandCraft;
    }

    public List<Long> getEligibleLineIds() {
        return eligibleLineIds;
    }
}
