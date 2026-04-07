package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.RuntimeStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class FulfillabilityEvaluator {

    FulfillabilityAssessment evaluate(List<DemandItem> demands,
                                      LocalDate planStart,
                                      LocalDate defaultDeadline,
                                      Map<LocalDate, BigDecimal> shiftHoursByDay,
                                      Map<String, List<LineCapacity>> lineCapByModel,
                                      Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId) {
        if (demands == null || demands.isEmpty() || planStart == null || defaultDeadline == null) {
            return new FulfillabilityAssessment(true, 0, 0, 0, null);
        }

        List<DemandItem> targetDemands = pickTargetDemands(demands);
        Map<Long, BigDecimal> baseCapacityPerHourByLine = resolveBaseCapacityPerHourByLine(lineCapByModel);
        FulfillabilityAssessment worstGapAssessment = null;
        for (DemandItem demand : targetDemands) {
            if (demand == null || demand.required() <= 0) {
                continue;
            }
            LocalDate deadline = demand.earliestDeliveryDate() == null ? defaultDeadline : demand.earliestDeliveryDate();
            int idleCapacityBeforeDeadline = capacityBeforeDeadline(planStart, deadline, shiftHoursByDay,
                    baseCapacityPerHourByLine, runtimeViewByLineId, RuntimeStatus.IDLE);
            int requiredInsertQuantity = Math.max(demand.required() - idleCapacityBeforeDeadline, 0);
            if (requiredInsertQuantity <= 0) {
                continue;
            }

            List<Integer> runningLineCapacities = capacityByLineBeforeDeadline(planStart, deadline, shiftHoursByDay,
                    baseCapacityPerHourByLine, runtimeViewByLineId, RuntimeStatus.RUNNING);
            int requiredInsertLineCount = estimateRequiredInsertLineCount(requiredInsertQuantity, runningLineCapacities);
            FulfillabilityAssessment candidate = new FulfillabilityAssessment(false, idleCapacityBeforeDeadline,
                    requiredInsertQuantity, requiredInsertLineCount, deadline.toString());
            if (shouldReplaceWorstGap(worstGapAssessment, candidate)) {
                worstGapAssessment = candidate;
            }
        }
        if (worstGapAssessment != null) {
            return worstGapAssessment;
        }

        int minRequiredQuantity = targetDemands.stream().mapToInt(DemandItem::required).filter(value -> value > 0).min().orElse(0);
        if (minRequiredQuantity <= 0) {
            return new FulfillabilityAssessment(true, 0, 0, 0, null);
        }
        LocalDate earliestDeadline = targetDemands.stream()
                .map(DemandItem::earliestDeliveryDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(defaultDeadline);
        int idleCapacityBeforeDeadline = capacityBeforeDeadline(planStart, earliestDeadline, shiftHoursByDay,
                baseCapacityPerHourByLine, runtimeViewByLineId, RuntimeStatus.IDLE);
        return new FulfillabilityAssessment(true, idleCapacityBeforeDeadline, 0, 0, earliestDeadline.toString());
    }

    private List<DemandItem> pickTargetDemands(List<DemandItem> demands) {
        List<DemandItem> lockedInsertDemands = new ArrayList<>();
        for (DemandItem demand : demands) {
            if (demand.lockedInsert()) {
                lockedInsertDemands.add(demand);
            }
        }
        return lockedInsertDemands.isEmpty() ? demands : lockedInsertDemands;
    }

    private boolean shouldReplaceWorstGap(FulfillabilityAssessment current, FulfillabilityAssessment candidate) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (candidate.getRequiredInsertQuantity() != current.getRequiredInsertQuantity()) {
            return candidate.getRequiredInsertQuantity() > current.getRequiredInsertQuantity();
        }
        if (candidate.getInsertDeadline() == null) {
            return false;
        }
        if (current.getInsertDeadline() == null) {
            return true;
        }
        return candidate.getInsertDeadline().compareTo(current.getInsertDeadline()) < 0;
    }

    private Map<Long, BigDecimal> resolveBaseCapacityPerHourByLine(Map<String, List<LineCapacity>> lineCapByModel) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (lineCapByModel == null || lineCapByModel.isEmpty()) {
            return result;
        }
        for (List<LineCapacity> capacities : lineCapByModel.values()) {
            for (LineCapacity capacity : capacities) {
                if (capacity == null || capacity.lineId == null || capacity.capacityPerHour == null) {
                    continue;
                }
                BigDecimal normalized = capacity.capacityPerHour.max(BigDecimal.ZERO);
                result.merge(capacity.lineId, normalized, BigDecimal::max);
            }
        }
        return result;
    }

    private int capacityBeforeDeadline(LocalDate planStart,
                                       LocalDate deadline,
                                       Map<LocalDate, BigDecimal> shiftHoursByDay,
                                       Map<Long, BigDecimal> baseCapacityPerHourByLine,
                                       Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId,
                                       int targetStatus) {
        int total = 0;
        for (Map.Entry<Long, BigDecimal> entry : baseCapacityPerHourByLine.entrySet()) {
            PlanningSnapshot.LineRuntimeView runtimeView = runtimeViewByLineId.get(entry.getKey());
            if (!hasStatus(runtimeView, targetStatus)) {
                continue;
            }
            BigDecimal capacityPerHour = resolveCapacityPerHour(runtimeView, entry.getValue());
            LocalDate cursor = planStart;
            while (!cursor.isAfter(deadline)) {
                BigDecimal hours = shiftHoursByDay.getOrDefault(cursor, BigDecimal.ZERO).max(BigDecimal.ZERO);
                total += capacityPerHour.multiply(hours).setScale(0, RoundingMode.FLOOR).intValue();
                cursor = cursor.plusDays(1);
            }
        }
        return Math.max(total, 0);
    }

    private List<Integer> capacityByLineBeforeDeadline(LocalDate planStart,
                                                       LocalDate deadline,
                                                       Map<LocalDate, BigDecimal> shiftHoursByDay,
                                                       Map<Long, BigDecimal> baseCapacityPerHourByLine,
                                                       Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId,
                                                       int targetStatus) {
        List<Integer> capacities = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : baseCapacityPerHourByLine.entrySet()) {
            PlanningSnapshot.LineRuntimeView runtimeView = runtimeViewByLineId.get(entry.getKey());
            if (!hasStatus(runtimeView, targetStatus)) {
                continue;
            }
            BigDecimal capacityPerHour = resolveCapacityPerHour(runtimeView, entry.getValue());
            int lineCapacity = 0;
            LocalDate cursor = planStart;
            while (!cursor.isAfter(deadline)) {
                BigDecimal hours = shiftHoursByDay.getOrDefault(cursor, BigDecimal.ZERO).max(BigDecimal.ZERO);
                lineCapacity += capacityPerHour.multiply(hours).setScale(0, RoundingMode.FLOOR).intValue();
                cursor = cursor.plusDays(1);
            }
            if (lineCapacity > 0) {
                capacities.add(lineCapacity);
            }
        }
        capacities.sort(Comparator.reverseOrder());
        return capacities;
    }

    private int estimateRequiredInsertLineCount(int requiredInsertQuantity, List<Integer> runningLineCapacities) {
        if (requiredInsertQuantity <= 0) {
            return 0;
        }
        int covered = 0;
        int lineCount = 0;
        for (Integer lineCapacity : runningLineCapacities) {
            if (lineCapacity == null || lineCapacity <= 0) {
                continue;
            }
            covered += lineCapacity;
            lineCount += 1;
            if (covered >= requiredInsertQuantity) {
                return lineCount;
            }
        }
        return runningLineCapacities.isEmpty() ? 0 : lineCount;
    }

    private BigDecimal resolveCapacityPerHour(PlanningSnapshot.LineRuntimeView runtimeView, BigDecimal baseCapacityPerHour) {
        if (runtimeView != null && runtimeView.getCurrentCapacity() != null && runtimeView.getCurrentCapacity().compareTo(BigDecimal.ZERO) > 0) {
            return runtimeView.getCurrentCapacity();
        }
        return baseCapacityPerHour == null ? BigDecimal.ZERO : baseCapacityPerHour;
    }

    private boolean hasStatus(PlanningSnapshot.LineRuntimeView runtimeView, int status) {
        return runtimeView != null && runtimeView.getStatus() != null && runtimeView.getStatus() == status;
    }
}
