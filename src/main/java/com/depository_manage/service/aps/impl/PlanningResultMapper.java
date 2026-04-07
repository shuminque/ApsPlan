package com.depository_manage.service.aps.impl;

import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewDailyDTO;
import com.depository_manage.pojo.shift.PlanPreviewOrderDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class PlanningResultMapper {

    private final DateTimeFormatter dateTimeFormatter;
    private final String planColor;

    PlanningResultMapper(DateTimeFormatter dateTimeFormatter, String planColor) {
        this.dateTimeFormatter = dateTimeFormatter;
        this.planColor = planColor;
    }

    PlanPreviewResponseDTO toPlanPreviewResponse(PlanningResult result,
                                                 LocalDateTime planStartAt,
                                                 Map<LocalDate, BigDecimal> shiftHoursByDay) {
        return toPlanPreviewResponse(result, planStartAt, shiftHoursByDay, null, null);
    }

    PlanPreviewResponseDTO toPlanPreviewResponse(PlanningResult result,
                                                 LocalDateTime planStartAt,
                                                 Map<LocalDate, BigDecimal> shiftHoursByDay,
                                                 PlanningSnapshot snapshot,
                                                 LocalDate endExclusive) {
        PlanPreviewResponseDTO response = new PlanPreviewResponseDTO();
        response.setEvents(mergeSlicesToEvents(result.getSlices(), planStartAt, shiftHoursByDay));
        response.setPlanStart(result.getActualStart() == null ? null : result.getActualStart().format(dateTimeFormatter));
        response.setPlanEnd(result.getActualEnd() == null ? null : result.getActualEnd().format(dateTimeFormatter));
        response.setOrders(buildOrderPreviewRows(result.getDemands()));
        response.setDailyOutputs(buildDailyPreviewRows(result.getSlices()));
        response.setSqueezedOrderCount(result.getMetrics().getSqueezedOrderCount());
        response.setDelayedDays(result.getMetrics().getDelayedDays());
        response.setInsertFulfillmentRate(result.getMetrics().getInsertFulfillmentRate());
        response.setInsertSuggestion(buildInsertSuggestion(result, snapshot, endExclusive));
        return response;
    }

    private PlanPreviewResponseDTO.InsertSuggestionDTO buildInsertSuggestion(PlanningResult result,
                                                                             PlanningSnapshot snapshot,
                                                                             LocalDate endExclusive) {
        PlanPreviewResponseDTO.InsertSuggestionDTO suggestion = new PlanPreviewResponseDTO.InsertSuggestionDTO();
        if (snapshot == null || endExclusive == null) {
            return suggestion;
        }
        int insertGap = result.getDemands().stream()
                .mapToInt(d -> Math.max(0, d.required() - d.plannedQuantity()))
                .sum();

        if (insertGap <= 0) {
            suggestion.setRequiredInsertLineCount(0);
            return suggestion;
        }

        Map<Long, String> lineNameById = snapshot.getProductionLines().stream()
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(line -> line.getId(), line -> Optional.ofNullable(line.getLineName()).orElse(""), (a, b) -> a));
        Map<Long, Integer> releasableByLine = new HashMap<>();
        for (Map.Entry<LineDayKey, Integer> entry : snapshot.getRemainingCapacityByLineDay().entrySet()) {
            LineDayKey key = entry.getKey();
            if (key == null || key.getLineId() == null || key.getDay() == null || !key.getDay().isBefore(endExclusive)) {
                continue;
            }
            int dayCapacity = Math.max(0, Optional.ofNullable(entry.getValue()).orElse(0));
            releasableByLine.merge(key.getLineId(), dayCapacity, Integer::sum);
        }

        List<PlanPreviewResponseDTO.CandidateLineDTO> candidates = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : releasableByLine.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            PlanPreviewResponseDTO.CandidateLineDTO candidate = new PlanPreviewResponseDTO.CandidateLineDTO();
            candidate.setLineId(entry.getKey());
            candidate.setLineName(lineNameById.getOrDefault(entry.getKey(), "产线-" + entry.getKey()));
            candidate.setReleasableCapacity(entry.getValue());
            PlanningSnapshot.LineRuntimeView runtimeView = snapshot.getRuntimeViewByLineId().get(entry.getKey());
            candidate.setCurrentModel(runtimeView == null ? null : runtimeView.getCurrentModel());
            candidate.setRiskTag(resolveRiskTag(runtimeView, entry.getValue()));
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparing(PlanPreviewResponseDTO.CandidateLineDTO::getReleasableCapacity,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(c -> Optional.ofNullable(c.getLineName()).orElse("")));
        suggestion.setCandidateLines(candidates);
        suggestion.setRequiredInsertLineCount(calculateRequiredLineCount(insertGap, candidates));
        return suggestion;
    }

    private int calculateRequiredLineCount(int insertGap, List<PlanPreviewResponseDTO.CandidateLineDTO> candidates) {
        if (insertGap <= 0 || candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int cumulative = 0;
        int count = 0;
        for (PlanPreviewResponseDTO.CandidateLineDTO candidate : candidates) {
            int capacity = Math.max(0, Optional.ofNullable(candidate.getReleasableCapacity()).orElse(0));
            if (capacity <= 0) {
                continue;
            }
            cumulative += capacity;
            count++;
            if (cumulative >= insertGap) {
                return count;
            }
        }
        return candidates.size();
    }

    private String resolveRiskTag(PlanningSnapshot.LineRuntimeView runtimeView, int releasableCapacity) {
        if (releasableCapacity <= 0) {
            return "NO_CAPACITY";
        }
        if (runtimeView == null) {
            return "RUNTIME_UNKNOWN";
        }
        if (runtimeView.getStatus() != null && runtimeView.getStatus() == 0) {
            return "LINE_STOPPED";
        }
        String currentModel = runtimeView.getCurrentModel();
        if (currentModel == null || currentModel.trim().isEmpty()) {
            return "MODEL_UNKNOWN";
        }
        return "LOW";
    }

    PlanningResult.Metrics calculateMetrics(List<DemandItem> demands, LocalDate endExclusive) {
        return new PlanningResult.Metrics(
                calculateSqueezedOrderCount(demands),
                calculateDelayedDays(demands, endExclusive),
                calculateInsertFulfillmentRate(demands)
        );
    }

    private List<CalendarEventDTO> mergeSlicesToEvents(List<PlanSlice> slices,
                                                        LocalDateTime planStartAt,
                                                        Map<LocalDate, BigDecimal> shiftHoursByDay) {
        List<CalendarEventDTO> result = new ArrayList<>();
        if (slices.isEmpty()) {
            return result;
        }

        slices.sort(Comparator
                .comparing(PlanSlice::mergeKey)
                .thenComparing(PlanSlice::day));

        PlanSlice current = slices.get(0);
        LocalDate blockStart = current.day();
        LocalDate blockEnd = current.day();
        int blockQty = current.quantity();

        for (int i = 1; i < slices.size(); i++) {
            PlanSlice next = slices.get(i);
            boolean sameBlock = current.mergeKey().equals(next.mergeKey())
                    && blockEnd.plusDays(1).equals(next.day());
            if (sameBlock) {
                blockEnd = next.day();
                blockQty += next.quantity();
                continue;
            }
            result.add(buildEvent(blockStart, blockEnd.plusDays(1), current, blockQty, planStartAt, shiftHoursByDay));
            current = next;
            blockStart = next.day();
            blockEnd = next.day();
            blockQty = next.quantity();
        }
        result.add(buildEvent(blockStart, blockEnd.plusDays(1), current, blockQty, planStartAt, shiftHoursByDay));
        return result;
    }

    private List<PlanPreviewOrderDTO> buildOrderPreviewRows(List<DemandItem> demands) {
        Map<String, List<DemandItem>> grouped = demands.stream()
                .collect(Collectors.groupingBy(item -> String.join("||",
                        Optional.ofNullable(item.customer()).orElse(""),
                        Optional.ofNullable(item.outerInnerRing()).orElse(""),
                        Optional.ofNullable(item.model()).orElse(""))));
        List<PlanPreviewOrderDTO> rows = new ArrayList<>();
        for (List<DemandItem> groupItems : grouped.values()) {
            if (groupItems.isEmpty()) {
                continue;
            }
            DemandItem item = groupItems.get(0);
            PlanPreviewOrderDTO row = new PlanPreviewOrderDTO();
            row.setCustomer(item.customer());
            row.setOuterInnerRing(item.outerInnerRing());
            row.setModel(item.model());
            row.setPriority(groupItems.stream().map(DemandItem::priority).max(Integer::compareTo).orElse(item.priority()));
            LocalDate earliest = groupItems.stream()
                    .map(DemandItem::earliestDeliveryDate)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            row.setEarliestDeliveryDate(earliest == null ? "" : earliest.toString());
            row.setCurrentInventory(item.currentInventory());
            row.setOrderCount(groupItems.stream().mapToInt(DemandItem::orderDemandQuantity).max().orElse(0));
            row.setSafetyStockQuantity(item.safetyStockQuantity());
            int plannedBaseQuantity = Math.max(0, row.getOrderCount()
                    + Optional.ofNullable(row.getSafetyStockQuantity()).orElse(0)
                    - Optional.ofNullable(row.getCurrentInventory()).orElse(0));
            row.setRequiredQuantity(plannedBaseQuantity);
            row.setPlannedQuantity(plannedBaseQuantity);
            row.setPlannedDays(groupItems.stream()
                    .flatMap(d -> d.plannedDays().stream())
                    .collect(Collectors.toSet()).size());
            row.setOrderIds(groupItems.stream()
                    .map(DemandItem::orderId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList()));
            rows.add(row);
        }
        rows.sort(Comparator.comparing((PlanPreviewOrderDTO row) -> Optional.ofNullable(row.getEarliestDeliveryDate()).orElse(""))
                .thenComparing(row -> Optional.ofNullable(row.getCustomer()).orElse(""))
                .thenComparing(row -> Optional.ofNullable(row.getOuterInnerRing()).orElse(""))
                .thenComparing(row -> Optional.ofNullable(row.getModel()).orElse("")));
        return rows;
    }

    private List<PlanPreviewDailyDTO> buildDailyPreviewRows(List<PlanSlice> slices) {
        List<PlanPreviewDailyDTO> rows = new ArrayList<>();
        slices.sort(Comparator.comparing(PlanSlice::day)
                .thenComparing(PlanSlice::customer)
                .thenComparing(PlanSlice::outerInnerRing)
                .thenComparing(PlanSlice::model)
                .thenComparing(PlanSlice::lineName));
        for (PlanSlice slice : slices) {
            PlanPreviewDailyDTO row = new PlanPreviewDailyDTO();
            row.setDay(slice.day().toString());
            row.setLineName(slice.lineName());
            row.setCustomer(slice.customer());
            row.setOuterInnerRing(slice.outerInnerRing());
            row.setModel(slice.model());
            row.setQuantity(slice.quantity());
            rows.add(row);
        }
        return rows;
    }

    private CalendarEventDTO buildEvent(LocalDate startInclusive,
                                        LocalDate endExclusive,
                                        PlanSlice slice,
                                        int quantity,
                                        LocalDateTime planStartAt,
                                        Map<LocalDate, BigDecimal> shiftHoursByDay) {
        CalendarEventDTO dto = new CalendarEventDTO();
        dto.setTitle(String.format("[排产] %s %s/%s %s x %,d", slice.lineName(), slice.customer(), slice.outerInnerRing(), slice.model(), quantity));
        LocalDateTime eventStart = startInclusive.atStartOfDay();
        if (planStartAt != null && startInclusive.equals(planStartAt.toLocalDate())) {
            eventStart = planStartAt;
        }
        dto.setStart(eventStart.format(dateTimeFormatter));
        dto.setEnd(endExclusive.atStartOfDay().format(dateTimeFormatter));
        dto.setColor(planColor);
        dto.setEventType("PLAN");
        dto.setLineId(slice.lineId());
        dto.setSource("RULE_PRIORITY");
        dto.setLineName(slice.lineName());
        dto.setCustomer(slice.customer());
        dto.setOuterInnerRing(slice.outerInnerRing());
        dto.setModel(slice.model());
        dto.setQuantity(quantity);
        dto.setOrderDemandQty(quantity);
        dto.setSafetyDemandQty(0);
        applyEventMetrics(dto, startInclusive, endExclusive, quantity, slice.capacityPerHour(), shiftHoursByDay);
        return dto;
    }

    private void applyEventMetrics(CalendarEventDTO dto,
                                   LocalDate startInclusive,
                                   LocalDate endExclusive,
                                   int quantity,
                                   BigDecimal capacityPerHour,
                                   Map<LocalDate, BigDecimal> shiftHoursByDay) {
        long plannedDays = Math.max(1L, ChronoUnit.DAYS.between(startInclusive, endExclusive));
        dto.setPlannedDays(plannedDays);
        BigDecimal dailyOutput = BigDecimal.valueOf(quantity)
                .divide(BigDecimal.valueOf(plannedDays), 2, RoundingMode.HALF_UP);
        dto.setDailyOutput(dailyOutput);
        dto.setCapacityPerHour(capacityPerHour);
        if (capacityPerHour == null) {
            dto.setDailyOutput(null);
            dto.setAvgDailyWorkHours(null);
            dto.setMetricDiagnosticTag("MISSING_CAPACITY_CONFIG");
            return;
        }
        if (capacityPerHour.compareTo(BigDecimal.ZERO) <= 0) {
            dto.setDailyOutput(null);
            dto.setAvgDailyWorkHours(null);
            dto.setMetricDiagnosticTag("INVALID_CAPACITY_CONFIG");
            return;
        }
        dto.setAvgDailyWorkHours(dailyOutput.divide(capacityPerHour, 2, RoundingMode.HALF_UP));
        dto.setMetricDiagnosticTag("OK");
    }

    private int calculateSqueezedOrderCount(List<DemandItem> demands) {
        return (int) demands.stream()
                .filter(demand -> !demand.lockedInsert())
                .filter(demand -> demand.plannedQuantity() < demand.required())
                .count();
    }

    private int calculateDelayedDays(List<DemandItem> demands, LocalDate endExclusive) {
        int delayedDays = 0;
        for (DemandItem demand : demands) {
            LocalDate dueDate = demand.earliestDeliveryDate();
            if (dueDate == null) {
                continue;
            }
            LocalDate completionDate = demand.lastPlannedDate().orElse(endExclusive.minusDays(1));
            if (completionDate.isAfter(dueDate)) {
                delayedDays += (int) ChronoUnit.DAYS.between(dueDate, completionDate);
            }
        }
        return Math.max(delayedDays, 0);
    }

    private BigDecimal calculateInsertFulfillmentRate(List<DemandItem> demands) {
        int required = demands.stream()
                .filter(DemandItem::lockedInsert)
                .mapToInt(DemandItem::required)
                .sum();
        if (required <= 0) {
            return BigDecimal.ONE;
        }
        int planned = demands.stream()
                .filter(DemandItem::lockedInsert)
                .mapToInt(DemandItem::plannedQuantity)
                .sum();
        return BigDecimal.valueOf(planned)
                .divide(BigDecimal.valueOf(required), 4, RoundingMode.HALF_UP);
    }
}
