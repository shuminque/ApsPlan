package com.depository_manage.service.aps.impl;

public class FulfillabilityAssessment {

    private final boolean canFulfillByIdleLines;
    private final int idleCapacityBeforeDeadline;
    private final int requiredInsertQuantity;
    private final int requiredInsertLineCount;

    public FulfillabilityAssessment(boolean canFulfillByIdleLines,
                                    int idleCapacityBeforeDeadline,
                                    int requiredInsertQuantity,
                                    int requiredInsertLineCount) {
        this.canFulfillByIdleLines = canFulfillByIdleLines;
        this.idleCapacityBeforeDeadline = idleCapacityBeforeDeadline;
        this.requiredInsertQuantity = requiredInsertQuantity;
        this.requiredInsertLineCount = requiredInsertLineCount;
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
}
