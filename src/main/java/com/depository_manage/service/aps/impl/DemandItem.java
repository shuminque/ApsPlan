package com.depository_manage.service.aps.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.depository_manage.service.aps.impl.ProductionPlanningServiceImpl.normalizeCraft;

public class DemandItem implements PlannableDemand {
    private final Long orderId;
    private final String customer;
    private final String outerInnerRing;
    private final String model;
    private final String craft;
    private final int required;
    private final int currentInventory;
    private final int orderCount;
    private final int orderDemandQuantity;
    private final int safetyStockQuantity;
    private final int priority;
    private final boolean lockedInsert;
    private int coveredQuantity;
    private int plannedQuantity;
    private final Date earliestDelivery;
    private final Set<LocalDate> plannedDays = new HashSet<>();
    private final LocalDateTime earliestStartAt;
    private final LocalDate currentDate;
    private final ZoneId zoneId;

    public DemandItem(Long orderId,
                      String customer,
                      String outerInnerRing,
                      String model,
                      String craft,
                      int required,
                      int currentInventory,
                      int orderCount,
                      int orderDemandQuantity,
                      int safetyStockQuantity,
                      Date earliestDelivery,
                      int priority,
                      boolean lockedInsert,
                      LocalDateTime earliestStartAt,
                      LocalDate currentDate,
                      ZoneId zoneId) {
        this.orderId = orderId;
        this.customer = customer;
        this.outerInnerRing = outerInnerRing;
        this.model = model;
        this.craft = normalizeCraft(craft);
        this.required = required;
        this.currentInventory = currentInventory;
        this.orderCount = orderCount;
        this.orderDemandQuantity = orderDemandQuantity;
        this.safetyStockQuantity = safetyStockQuantity;
        this.priority = priority;
        this.lockedInsert = lockedInsert;
        this.coveredQuantity = 0;
        this.plannedQuantity = 0;
        this.earliestDelivery = earliestDelivery;
        this.earliestStartAt = earliestStartAt == null ? null : earliestStartAt.withNano(0);
        this.currentDate = currentDate;
        this.zoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
    }

    public Long orderId() { return orderId; }
    public String customer() { return customer; }
    public String outerInnerRing() { return outerInnerRing; }
    public String model() { return model; }
    public int required() { return required; }
    public int currentInventory() { return currentInventory; }
    public Set<LocalDate> plannedDays() { return plannedDays; }

    @Override
    public int remaining() {
        return Math.max(0, required - coveredQuantity);
    }

    public int plannedQuantity() { return plannedQuantity; }
    public int orderCount() { return orderCount; }
    public int orderDemandQuantity() { return orderDemandQuantity; }
    public int safetyStockQuantity() { return safetyStockQuantity; }
    public boolean lockedInsert() { return lockedInsert; }
    public int priority() { return priority; }

    public int deliveryUrgencyDays() {
        if (earliestDelivery == null) {
            return Integer.MAX_VALUE;
        }
        LocalDate d = earliestDeliveryDate();
        return (int) ChronoUnit.DAYS.between(currentDate, d);
    }

    public LocalDate earliestDeliveryDate() {
        if (earliestDelivery == null) {
            return null;
        }
        return earliestDelivery.toInstant().atZone(zoneId).toLocalDate();
    }

    @Override
    public LocalDate earliestStartDate() {
        return earliestStartAt == null ? null : earliestStartAt.toLocalDate();
    }

    @Override
    public void applyPlan(int quantity, LocalDate day) {
        if (quantity <= 0) {
            return;
        }
        plannedQuantity += quantity;
        coveredQuantity = Math.min(required, coveredQuantity + quantity);
        plannedDays.add(day);
    }

    public boolean isLaOrLb() {
        return "LA".equalsIgnoreCase(outerInnerRing) || "LB".equalsIgnoreCase(outerInnerRing);
    }

    public String requiredCraft() { return craft; }

    public String normalizedCustomer() { return normalize(customer); }

    public String activationKey() {
        return (lockedInsert ? "INSERT|" : "AUTO|") + normalizedCustomer() + "|" + normalize(outerInnerRing) + "|" + normalize(model);
    }

    public Optional<LocalDate> lastPlannedDate() {
        if (plannedDays.isEmpty()) {
            return Optional.empty();
        }
        return plannedDays.stream().max(LocalDate::compareTo);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
