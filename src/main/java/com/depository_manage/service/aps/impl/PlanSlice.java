package com.depository_manage.service.aps.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PlanSlice {
    private final LocalDate day;
    private final Long lineId;
    private final String lineName;
    private final String customer;
    private final String outerInnerRing;
    private final String model;
    private final int quantity;
    private final BigDecimal capacityPerHour;

    public PlanSlice(LocalDate day, Long lineId, String lineName, String customer, String outerInnerRing, String model, int quantity, BigDecimal capacityPerHour) {
        this.day = day;
        this.lineId = lineId;
        this.lineName = lineName;
        this.customer = customer;
        this.outerInnerRing = outerInnerRing;
        this.model = model;
        this.quantity = quantity;
        this.capacityPerHour = capacityPerHour;
    }

    public LocalDate day() { return day; }
    public Long lineId() { return lineId; }
    public String lineName() { return lineName; }
    public String customer() { return customer; }
    public String outerInnerRing() { return outerInnerRing; }
    public String model() { return model; }
    public int quantity() { return quantity; }
    public BigDecimal capacityPerHour() { return capacityPerHour; }

    public String mergeKey() {
        return lineName + "|" + customer + "|" + outerInnerRing + "|" + model;
    }
}
