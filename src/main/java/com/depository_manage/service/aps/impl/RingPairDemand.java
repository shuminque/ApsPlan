package com.depository_manage.service.aps.impl;

import java.time.LocalDate;
import java.util.List;

public class RingPairDemand implements ProductionPlanningServiceImpl.PlannableDemand {
    private final DemandItem laDemand;
    private final DemandItem lbDemand;
    private final String sharedSeries;
    private final List<LineCapacity> sharedBarLines;

    public RingPairDemand(DemandItem laDemand, DemandItem lbDemand, String sharedSeries, List<LineCapacity> sharedBarLines) {
        this.laDemand = laDemand;
        this.lbDemand = lbDemand;
        this.sharedSeries = sharedSeries;
        this.sharedBarLines = sharedBarLines;
    }

    @Override
    public int remaining() {
        return Math.max(laDemand.remaining(), lbDemand.remaining());
    }

    public Integer maxRequired() {
        return Math.max(laDemand.required(), lbDemand.required());
    }

    public int priority() {
        return Math.max(laDemand.priority(), lbDemand.priority());
    }

    public boolean lockedInsert() {
        return laDemand.lockedInsert() || lbDemand.lockedInsert();
    }

    public int deliveryUrgencyDays() {
        return Math.min(laDemand.deliveryUrgencyDays(), lbDemand.deliveryUrgencyDays());
    }

    public String customer() {
        return laDemand.customer();
    }

    public String laModel() {
        return laDemand.model();
    }

    public String lbModel() {
        return lbDemand.model();
    }

    public List<LineCapacity> sharedBarLines() {
        return sharedBarLines;
    }

    public String activationKey() {
        return laDemand.normalizedCustomer() + "|PAIR|" + normalize(sharedSeries) + "|" + normalize(laDemand.model()) + "|" + normalize(lbDemand.model());
    }

    @Override
    public LocalDate earliestStartDate() {
        LocalDate laStart = laDemand.earliestStartDate();
        LocalDate lbStart = lbDemand.earliestStartDate();
        if (laStart == null) {
            return lbStart;
        }
        if (lbStart == null) {
            return laStart;
        }
        return laStart.isAfter(lbStart) ? laStart : lbStart;
    }

    @Override
    public void applyPlan(int quantity, LocalDate day) {
        laDemand.applyPlan(quantity, day);
        lbDemand.applyPlan(quantity, day);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
