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
    private final String triggerGapModel;
    private final String triggerGapCraft;
    private final List<Long> triggerGapLineIds;

    public FulfillabilityAssessment(boolean canFulfillByIdleLines,
                                    int idleCapacityBeforeDeadline,
                                    int requiredInsertQuantity,
                                    int requiredInsertLineCount,
                                    String insertDeadline,
                                    String triggerGapModel,
                                    String triggerGapCraft,
                                    List<Long> triggerGapLineIds) {
        this.canFulfillByIdleLines = canFulfillByIdleLines;
        this.idleCapacityBeforeDeadline = idleCapacityBeforeDeadline;
        this.requiredInsertQuantity = requiredInsertQuantity;
        this.requiredInsertLineCount = requiredInsertLineCount;
        this.insertDeadline = insertDeadline;
        this.triggerGapModel = triggerGapModel;
        this.triggerGapCraft = triggerGapCraft;
        this.triggerGapLineIds = triggerGapLineIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<Long>(triggerGapLineIds));
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
        return triggerGapModel;
    }

    public String getTriggerGapCraft() {
        return triggerGapCraft;
    }

    public List<Long> getTriggerGapLineIds() {
        return triggerGapLineIds;
    }
}
