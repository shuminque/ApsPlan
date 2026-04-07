package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.RuntimeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

class FulfillabilityEvaluator {
    private static final Logger log = LoggerFactory.getLogger(FulfillabilityEvaluator.class);

    FulfillabilityAssessment evaluate(List<DemandItem> demands,
                                      LocalDate planStart,
                                      LocalDate defaultDeadline,
                                      Map<LocalDate, BigDecimal> shiftHoursByDay,
                                      Map<String, List<LineCapacity>> lineCapByModel,
                                      Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId) {
        if (demands == null || demands.isEmpty() || planStart == null || defaultDeadline == null) {
            return new FulfillabilityAssessment(true, 0, 0, 0, null, null, null, Collections.<Long>emptyList());
        }

        List<DemandItem> targetDemands = pickTargetDemands(demands);
        FulfillabilityAssessment worstGapAssessment = null;
        for (DemandItem demand : targetDemands) {
            if (demand == null || demand.required() <= 0) {
                continue;
            }
            List<LineCapacity> eligibleLines = resolveEligibleLinesForDemand(demand, lineCapByModel);
            Map<Long, BigDecimal> baseCapacityPerHourByLine = resolveBaseCapacityPerHourByLine(eligibleLines);
            log.info("fulfillability evaluate lines={}, demandModel={}, demandCraft={}",
                    baseCapacityPerHourByLine.keySet(), demand.model(), demand.requiredCraft());
            LocalDate deadline = demand.earliestDeliveryDate() == null ? defaultDeadline : demand.earliestDeliveryDate();
            int idleCapacityBeforeDeadline = capacityBeforeDeadline(planStart, deadline, shiftHoursByDay,
                    baseCapacityPerHourByLine, runtimeViewByLineId, RuntimeStatus.IDLE);
            int requiredInsertQuantity = Math.max(demand.required() - idleCapacityBeforeDeadline, 0);
            if (requiredInsertQuantity <= 0) {
                continue;
            }

            List<Integer> runningLineCapacities = capacityByLineBeforeDeadline(planStart, deadline, shiftHoursByDay,
                    baseCapacityPerHourByLine, runtimeViewByLineId, RuntimeStatus.RUNNING);
            logRunningCapacityDiagnostics(demand, requiredInsertQuantity, eligibleLines, baseCapacityPerHourByLine,
                    runtimeViewByLineId, runningLineCapacities);
            int requiredInsertLineCount = estimateRequiredInsertLineCount(requiredInsertQuantity, runningLineCapacities);
            List<Long> eligibleLineIds = resolveLineIds(eligibleLines);
            FulfillabilityAssessment candidate = new FulfillabilityAssessment(false, idleCapacityBeforeDeadline,
                    requiredInsertQuantity, requiredInsertLineCount, deadline.toString(),
                    demand.model(), demand.requiredCraft(), eligibleLineIds);
            if (shouldReplaceWorstGap(worstGapAssessment, candidate)) {
                worstGapAssessment = candidate;
            }
        }
        if (worstGapAssessment != null) {
            return worstGapAssessment;
        }

        int minRequiredQuantity = targetDemands.stream().mapToInt(DemandItem::required).filter(value -> value > 0).min().orElse(0);
        if (minRequiredQuantity <= 0) {
            return new FulfillabilityAssessment(true, 0, 0, 0, null, null, null, Collections.<Long>emptyList());
        }
        LocalDate earliestDeadline = targetDemands.stream()
                .map(DemandItem::earliestDeliveryDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(defaultDeadline);
        DemandItem minDemand = targetDemands.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.required() > 0)
                .min(Comparator.comparingInt(DemandItem::required))
                .orElse(null);
        Map<Long, BigDecimal> fallbackCapacityByLine = resolveBaseCapacityPerHourByLine(
                resolveEligibleLinesForDemand(minDemand, lineCapByModel)
        );
        int idleCapacityBeforeDeadline = capacityBeforeDeadline(planStart, earliestDeadline, shiftHoursByDay,
                fallbackCapacityByLine, runtimeViewByLineId, RuntimeStatus.IDLE);
        return new FulfillabilityAssessment(true, idleCapacityBeforeDeadline, 0, 0, earliestDeadline.toString(),
                minDemand == null ? null : minDemand.model(),
                minDemand == null ? null : minDemand.requiredCraft(),
                resolveLineIds(resolveEligibleLinesForDemand(minDemand, lineCapByModel)));
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

    private Map<Long, BigDecimal> resolveBaseCapacityPerHourByLine(List<LineCapacity> capacities) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (capacities == null || capacities.isEmpty()) {
            return result;
        }
        for (LineCapacity capacity : capacities) {
            if (capacity == null || capacity.lineId == null || capacity.capacityPerHour == null) {
                continue;
            }
            BigDecimal normalized = capacity.capacityPerHour.max(BigDecimal.ZERO);
            result.merge(capacity.lineId, normalized, BigDecimal::max);
        }
        return result;
    }

    private List<LineCapacity> resolveEligibleLinesForDemand(DemandItem demand,
                                                             Map<String, List<LineCapacity>> lineCapByModel) {
        if (demand == null) {
            return Collections.emptyList();
        }
        List<LineCapacity> matchedLines = findMatchingLines(demand.model(), lineCapByModel);
        return matchedLines.stream()
                .filter(line -> line.matchesCraft(demand.requiredCraft()))
                .collect(Collectors.toList());
    }

    private List<Long> resolveLineIds(List<LineCapacity> capacities) {
        if (capacities == null || capacities.isEmpty()) {
            return Collections.emptyList();
        }
        return capacities.stream()
                .map(capacity -> capacity == null ? null : capacity.lineId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<LineCapacity> findMatchingLines(String demandModel, Map<String, List<LineCapacity>> lineCapByModel) {
        if (demandModel == null || demandModel.trim().isEmpty() || lineCapByModel == null || lineCapByModel.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedDemandModel = demandModel.trim();
        List<LineCapacity> exactMatches = lineCapByModel.get(normalizedDemandModel);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            return exactMatches;
        }
        List<LineCapacity> seriesMatches = new ArrayList<LineCapacity>();
        for (Map.Entry<String, List<LineCapacity>> entry : lineCapByModel.entrySet()) {
            if (!isSeriesMatch(normalizedDemandModel, entry.getKey())) {
                continue;
            }
            seriesMatches.addAll(entry.getValue());
        }
        return seriesMatches;
    }

    private boolean isSeriesMatch(String demandModel, String configModel) {
        if (demandModel == null || demandModel.trim().isEmpty() || configModel == null || configModel.trim().isEmpty()) {
            return false;
        }
        String normalizedDemandModel = normalizeModelForSeriesMatch(demandModel);
        String normalizedConfigModel = normalizeModelForSeriesMatch(configModel);
        if (normalizedDemandModel.isEmpty() || normalizedConfigModel.isEmpty()) {
            return false;
        }
        return normalizedDemandModel.startsWith(normalizedConfigModel)
                || normalizedDemandModel.contains(normalizedConfigModel);
    }

    private String normalizeModelForSeriesMatch(String model) {
        return model == null ? "" : model.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
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
        if (runningLineCapacities == null || runningLineCapacities.isEmpty()) {
            return -1;
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
        return lineCount;
    }

    private void logRunningCapacityDiagnostics(DemandItem demand,
                                               int requiredInsertQuantity,
                                               List<LineCapacity> eligibleLines,
                                               Map<Long, BigDecimal> baseCapacityPerHourByLine,
                                               Map<Long, PlanningSnapshot.LineRuntimeView> runtimeViewByLineId,
                                               List<Integer> runningLineCapacities) {
        List<Long> runningStatusLineIds = new ArrayList<>();
        for (Long lineId : baseCapacityPerHourByLine.keySet()) {
            if (hasStatus(runtimeViewByLineId.get(lineId), RuntimeStatus.RUNNING)) {
                runningStatusLineIds.add(lineId);
            }
        }
        String emptyReason = "NONE";
        if (requiredInsertQuantity > 0 && (runningLineCapacities == null || runningLineCapacities.isEmpty())) {
            if (eligibleLines == null || eligibleLines.isEmpty() || baseCapacityPerHourByLine.isEmpty()) {
                emptyReason = "LINE_MATCH_FILTERED";
            } else if (runningStatusLineIds.isEmpty()) {
                emptyReason = "STATUS_FILTERED";
            } else {
                emptyReason = "CAPACITY_CALCULATED_ZERO";
            }
        }
        log.info("fulfillability runningLineCapacities detail: model={}, craft={}, requiredInsertQuantity={}, eligibleLineCount={}, baseLineCount={}, runningStatusLineIds={}, runningLineCapacities={}, emptyReason={}",
                demand == null ? null : demand.model(),
                demand == null ? null : demand.requiredCraft(),
                requiredInsertQuantity,
                eligibleLines == null ? 0 : eligibleLines.size(),
                baseCapacityPerHourByLine.size(),
                runningStatusLineIds,
                runningLineCapacities,
                emptyReason);
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
