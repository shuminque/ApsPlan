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
            return new FulfillabilityAssessment(true, 0, 0, 0);
        }

        List<DemandItem> targetDemands = pickTargetDemands(demands);
        int requiredQuantity = targetDemands.stream().mapToInt(DemandItem::required).sum();
        if (requiredQuantity <= 0) {
            return new FulfillabilityAssessment(true, 0, 0, 0);
        }

        LocalDate deadline = targetDemands.stream()
                .map(DemandItem::earliestDeliveryDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(defaultDeadline);

        Map<Long, BigDecimal> baseCapacityPerHourByLine = resolveBaseCapacityPerHourByLine(lineCapByModel);
        int idleCapacityBeforeDeadline = capacityBeforeDeadline(planStart, deadline, shiftHoursByDay,
                baseCapacityPerHourByLine, runtimeViewByLineId, RuntimeStatus.IDLE);

        int requiredInsertQuantity = Math.max(requiredQuantity - idleCapacityBeforeDeadline, 0);
        if (requiredInsertQuantity <= 0) {
            return new FulfillabilityAssessment(true, idleCapacityBeforeDeadline, 0, 0);
        }

        List<Integer> runningLineCapacities = capacityByLineBeforeDeadline(planStart, deadline, shiftHoursByDay,
                baseCapacityPerHourByLine, runtimeViewByLineId, RuntimeStatus.RUNNING);
        int requiredInsertLineCount = estimateRequiredInsertLineCount(requiredInsertQuantity, runningLineCapacities);
        return new FulfillabilityAssessment(false, idleCapacityBeforeDeadline, requiredInsertQuantity, requiredInsertLineCount);
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
