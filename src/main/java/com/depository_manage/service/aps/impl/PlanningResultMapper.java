package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.RuntimeStatus;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewDailyDTO;
import com.depository_manage.pojo.shift.PlanPreviewOrderDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class PlanningResultMapper {
    private static final Logger log = LoggerFactory.getLogger(PlanningResultMapper.class);
    private static final int MANUAL_INSERT_LINE_REQUIRED = -1;
    private static final String MANUAL_INSERT_LINE_HINT = "需人工指定插单线";
    private static final String NO_ELIGIBLE_RUNNING_LINES = "NO_ELIGIBLE_RUNNING_LINES";
    private static final String EXCLUDED_DUE_TO_ORIGINAL_DELAY = "EXCLUDED_DUE_TO_ORIGINAL_DELAY";

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
        FulfillabilityAssessment assessment = result.getDiagnostics() instanceof FulfillabilityAssessment
                ? (FulfillabilityAssessment) result.getDiagnostics()
                : null;
        int requiredInsertQuantity = assessment == null ? 0 : Math.max(assessment.getRequiredInsertQuantity(), 0);
        boolean autoInsertTriggered = requiredInsertQuantity > 0;
        int rawRequiredInsertLineCount = assessment == null ? 0 : assessment.getRequiredInsertLineCount();
        boolean manualLineSelectionRequired = rawRequiredInsertLineCount == MANUAL_INSERT_LINE_REQUIRED;
        int requiredInsertLineCount = manualLineSelectionRequired ? 0 : Math.max(rawRequiredInsertLineCount, 0);
        response.setAutoInsertTriggered(autoInsertTriggered);
        response.setRequiredInsertQuantity(requiredInsertQuantity);
        response.setRequiredInsertLineCount(requiredInsertLineCount);
        response.setRequiredInsertLineHint(manualLineSelectionRequired ? MANUAL_INSERT_LINE_HINT : null);
        response.setInsertDeadline(assessment == null ? null : assessment.getInsertDeadline());
        response.setInsertSuggestion(buildInsertSuggestion(response, snapshot, endExclusive, autoInsertTriggered, assessment));
        return response;
    }

    private PlanPreviewResponseDTO.InsertSuggestionDTO buildInsertSuggestion(PlanPreviewResponseDTO response,
                                                                             PlanningSnapshot snapshot,
                                                                             LocalDate endExclusive,
                                                                             boolean autoInsertTriggered,
                                                                             FulfillabilityAssessment assessment) {
        PlanPreviewResponseDTO.InsertSuggestionDTO suggestion = new PlanPreviewResponseDTO.InsertSuggestionDTO();
        int rawRequiredInsertLineCount = assessment == null ? 0 : assessment.getRequiredInsertLineCount();
        boolean manualLineSelectionRequired = rawRequiredInsertLineCount == MANUAL_INSERT_LINE_REQUIRED;
        int requiredInsertLineCount = manualLineSelectionRequired ? 0 : Math.max(rawRequiredInsertLineCount, 0);
        int requiredInsertQuantity = assessment == null ? 0 : Math.max(assessment.getRequiredInsertQuantity(), 0);
        suggestion.setRequiredInsertLineCount(requiredInsertLineCount);
        suggestion.setRequiredInsertLineHint(manualLineSelectionRequired ? MANUAL_INSERT_LINE_HINT : null);
        if (snapshot == null || endExclusive == null) {
            return suggestion;
        }
        if (!autoInsertTriggered) {
            return suggestion;
        }

        Set<Long> eligibleLineIds = resolveEligibleLineIds(assessment);
        Map<Long, String> lineNameById = snapshot.getProductionLines().stream()
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(line -> line.getId(), line -> Optional.ofNullable(line.getLineName()).orElse(""), (a, b) -> a));
        Map<Long, ReleasableCapacityDetail> releasableByLine = calculateReleasableCapacityByLine(snapshot, assessment, endExclusive);

        List<PlanPreviewResponseDTO.CandidateLineDTO> candidates = new ArrayList<>();
        for (Long lineId : eligibleLineIds) {
            ReleasableCapacityDetail capacityDetail = releasableByLine.get(lineId);
            Integer releasableCapacity = capacityDetail == null ? null : capacityDetail.netReleasableCapacity;
            if (releasableCapacity == null || releasableCapacity <= 0) {
                continue;
            }
            PlanPreviewResponseDTO.CandidateLineDTO candidate = new PlanPreviewResponseDTO.CandidateLineDTO();
            candidate.setLineId(lineId);
            candidate.setLineName(lineNameById.getOrDefault(lineId, "产线-" + lineId));
            candidate.setReleasableCapacity(releasableCapacity);
            PlanningSnapshot.LineRuntimeView runtimeView = snapshot.getRuntimeViewByLineId().get(lineId);
            candidate.setCurrentModel(runtimeView == null ? null : runtimeView.getCurrentModel());
            candidate.setRiskTag(resolveRiskTag(runtimeView, releasableCapacity));
            if (capacityDetail != null) {
                candidate.setBaseCapacityPerHour(capacityDetail.baseCapacityPerHour);
                candidate.setRuntimeCapacityPerHour(capacityDetail.runtimeCapacityPerHour);
                candidate.setEffectiveCapacityPerHour(capacityDetail.effectiveCapacityPerHour);
                candidate.setTotalShiftHours(capacityDetail.totalShiftHours);
                candidate.setReleasableCapacityFormula(String.format(Locale.ROOT, "floor(%s × %s) = %d",
                        capacityDetail.effectiveCapacityPerHour.stripTrailingZeros().toPlainString(),
                        capacityDetail.totalShiftHours.stripTrailingZeros().toPlainString(),
                        capacityDetail.netReleasableCapacity));
            }
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparing(PlanPreviewResponseDTO.CandidateLineDTO::getReleasableCapacity,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(c -> Optional.ofNullable(c.getLineName()).orElse("")));
        log.info("insert suggestion eligible/candidate lines: assessment.eligibleLineIds={}, candidateLines={}",
                eligibleLineIds,
                candidates.stream()
                        .map(PlanPreviewResponseDTO.CandidateLineDTO::getLineId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
        suggestion.setCandidateLines(candidates);
        int totalCandidateCapacity = candidates.stream()
                .map(PlanPreviewResponseDTO.CandidateLineDTO::getReleasableCapacity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (requiredInsertQuantity > totalCandidateCapacity) {
            response.setInsertDelayRequired(true);
            response.setInsertShortageQuantity(requiredInsertQuantity - totalCandidateCapacity);
            Integer delayedDays = response.getDelayedDays();
            response.setSuggestedDelayedDays(delayedDays != null && delayedDays > 0 ? delayedDays : null);
        } else {
            response.setInsertDelayRequired(false);
            response.setInsertShortageQuantity(0);
            response.setSuggestedDelayedDays(null);
        }
        if (requiredInsertQuantity > 0 && candidates.isEmpty()) {
            suggestion.setDiagnosticTag(NO_ELIGIBLE_RUNNING_LINES);
        }
        if (!manualLineSelectionRequired) {
            suggestion.setRequiredInsertLineCount(estimateRequiredLineCount(requiredInsertQuantity, candidates));
        }
        return suggestion;
    }

    private Set<Long> resolveEligibleLineIds(FulfillabilityAssessment assessment) {
        List<Long> eligibleLineIds = assessment == null ? Collections.<Long>emptyList() : assessment.getEligibleLineIds();
        if (eligibleLineIds == null || eligibleLineIds.isEmpty()) {
            return Collections.emptySet();
        }
        return eligibleLineIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private int estimateRequiredLineCount(int requiredInsertQuantity,
                                          List<PlanPreviewResponseDTO.CandidateLineDTO> candidates) {
        if (requiredInsertQuantity <= 0 || candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int covered = 0;
        int lineCount = 0;
        for (PlanPreviewResponseDTO.CandidateLineDTO candidate : candidates) {
            if (candidate == null || candidate.getReleasableCapacity() == null || candidate.getReleasableCapacity() <= 0) {
                continue;
            }
            covered += candidate.getReleasableCapacity();
            lineCount += 1;
            if (covered >= requiredInsertQuantity) {
                return lineCount;
            }
        }
        return lineCount;
    }

    private static class ReleasableCapacityDetail {
        private final int netReleasableCapacity;
        private final BigDecimal baseCapacityPerHour;
        private final BigDecimal runtimeCapacityPerHour;
        private final BigDecimal effectiveCapacityPerHour;
        private final BigDecimal totalShiftHours;

        private ReleasableCapacityDetail(int netReleasableCapacity,
                                        BigDecimal baseCapacityPerHour,
                                        BigDecimal runtimeCapacityPerHour,
                                        BigDecimal effectiveCapacityPerHour,
                                        BigDecimal totalShiftHours) {
            this.netReleasableCapacity = netReleasableCapacity;
            this.baseCapacityPerHour = baseCapacityPerHour;
            this.runtimeCapacityPerHour = runtimeCapacityPerHour;
            this.effectiveCapacityPerHour = effectiveCapacityPerHour;
            this.totalShiftHours = totalShiftHours;
        }
    }

    private Map<Long, ReleasableCapacityDetail> calculateReleasableCapacityByLine(PlanningSnapshot snapshot,
                                                                 FulfillabilityAssessment assessment,
                                                                 LocalDate endExclusive) {
        if (snapshot == null || endExclusive == null) {
            return Collections.emptyMap();
        }
        LocalDate planStart = snapshot.getShiftHoursByDay().keySet().stream().min(LocalDate::compareTo).orElse(endExclusive);
        LocalDate deadline = endExclusive.minusDays(1);
        if (deadline.isBefore(planStart)) {
            return Collections.emptyMap();
        }
        String triggerModel = assessment == null ? null : assessment.getEligibleDemandModel();
        String triggerCraft = assessment == null ? null : assessment.getEligibleDemandCraft();
        if ((triggerModel == null || triggerModel.trim().isEmpty()) && (triggerCraft == null || triggerCraft.trim().isEmpty())) {
            return Collections.emptyMap();
        }
        List<LineCapacity> matchedCapacities = findMatchingCapacities(triggerModel, snapshot.getLineCapByModel());
        Map<Long, BigDecimal> baseCapacityPerHourByLine = new HashMap<>();
        for (LineCapacity capacity : matchedCapacities) {
            if (capacity == null || capacity.lineId == null || capacity.capacityPerHour == null || !capacity.matchesCraft(triggerCraft)) {
                continue;
            }
            BigDecimal normalized = capacity.capacityPerHour.max(BigDecimal.ZERO);
            baseCapacityPerHourByLine.merge(capacity.lineId, normalized, BigDecimal::max);
        }

        Map<Long, ReleasableCapacityDetail> releasableByLine = new HashMap<>();
        int requiredInsertQuantity = assessment == null ? 0 : Math.max(assessment.getRequiredInsertQuantity(), 0);
        LocalDateTime simulationStart = planStart.atStartOfDay();
        Map<Long, List<ProductionPlanItem>> committedByLine = snapshot.getCommittedPlanItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getLineId() != null)
                .collect(Collectors.groupingBy(ProductionPlanItem::getLineId));
        for (Map.Entry<Long, BigDecimal> entry : baseCapacityPerHourByLine.entrySet()) {
            Long lineId = entry.getKey();
            PlanningSnapshot.LineRuntimeView runtimeView = snapshot.getRuntimeViewByLineId().get(entry.getKey());
            if (!hasStatus(runtimeView, RuntimeStatus.RUNNING)) {
                continue;
            }
            BigDecimal baseCapacityPerHour = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue().max(BigDecimal.ZERO);
            BigDecimal runtimeCapacityPerHour = runtimeView == null || runtimeView.getCurrentCapacity() == null
                    ? BigDecimal.ZERO
                    : runtimeView.getCurrentCapacity().max(BigDecimal.ZERO);
            BigDecimal capacityPerHour = resolveCapacityPerHour(runtimeView, baseCapacityPerHour);
            int lineCapacity = 0;
            BigDecimal totalShiftHours = BigDecimal.ZERO;
            LocalDate cursor = planStart;
            while (!cursor.isAfter(deadline)) {
                BigDecimal hours = snapshot.getShiftHoursByDay().getOrDefault(cursor, BigDecimal.ZERO).max(BigDecimal.ZERO);
                totalShiftHours = totalShiftHours.add(hours);
                lineCapacity += capacityPerHour.multiply(hours).setScale(0, RoundingMode.FLOOR).intValue();
                cursor = cursor.plusDays(1);
            }
            int netReleasableCapacity = Math.max(lineCapacity, 0);
            if (netReleasableCapacity <= 0) {
                continue;
            }
            int simulatedInsertQty = requiredInsertQuantity <= 0
                    ? netReleasableCapacity
                    : Math.min(requiredInsertQuantity, netReleasableCapacity);
            if (willCauseOriginalOrderDelay(committedByLine.get(lineId), snapshot.getDeliveryDateByOrderId(),
                    simulationStart, capacityPerHour, simulatedInsertQty)) {
                log.info("exclude candidate line due to original-order delay. lineId={}, reason={}", lineId, EXCLUDED_DUE_TO_ORIGINAL_DELAY);
                continue;
            }
            releasableByLine.put(lineId, new ReleasableCapacityDetail(netReleasableCapacity, baseCapacityPerHour, runtimeCapacityPerHour, capacityPerHour, totalShiftHours));
        }
        return releasableByLine;
    }

    private boolean willCauseOriginalOrderDelay(List<ProductionPlanItem> committedItems,
                                                Map<Long, LocalDate> deliveryDateByOrderId,
                                                LocalDateTime insertStart,
                                                BigDecimal capacityPerHour,
                                                int insertQuantity) {
        if (committedItems == null || committedItems.isEmpty() || deliveryDateByOrderId == null || deliveryDateByOrderId.isEmpty()) {
            return false;
        }
        if (insertStart == null || capacityPerHour == null || capacityPerHour.compareTo(BigDecimal.ZERO) <= 0 || insertQuantity <= 0) {
            return false;
        }
        long insertMinutes = BigDecimal.valueOf(insertQuantity)
                .multiply(BigDecimal.valueOf(60))
                .divide(capacityPerHour, 0, RoundingMode.CEILING)
                .longValue();
        if (insertMinutes <= 0) {
            return false;
        }
        LocalDateTime cursor = insertStart.plusMinutes(insertMinutes);
        List<ProductionPlanItem> sortedItems = committedItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getStartDate() != null && item.getEndDate() != null)
                .sorted(Comparator.comparing(ProductionPlanItem::getStartDate))
                .collect(Collectors.toList());
        for (ProductionPlanItem item : sortedItems) {
            LocalDateTime originalStart = toLocalDateTime(item.getStartDate());
            LocalDateTime originalEnd = toLocalDateTime(item.getEndDate());
            if (!originalEnd.isAfter(insertStart)) {
                continue;
            }
            Duration duration = Duration.between(originalStart, originalEnd);
            if (duration.isNegative() || duration.isZero()) {
                continue;
            }
            LocalDateTime shiftedStart = originalStart.isAfter(cursor) ? originalStart : cursor;
            LocalDateTime shiftedEnd = shiftedStart.plus(duration);
            cursor = shiftedEnd;

            Long orderId = item.getOrderId();
            LocalDate deliveryDate = orderId == null ? null : deliveryDateByOrderId.get(orderId);
            if (deliveryDate == null) {
                continue;
            }
            LocalDateTime deliveryDeadline = deliveryDate.plusDays(1).atStartOfDay();
            boolean wasOnTime = !originalEnd.isAfter(deliveryDeadline);
            boolean becomesDelayed = shiftedEnd.isAfter(deliveryDeadline);
            if (wasOnTime && becomesDelayed) {
                return true;
            }
        }
        return false;
    }

    private LocalDateTime toLocalDateTime(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(value.toInstant(), java.time.ZoneId.systemDefault());
    }

    private List<LineCapacity> findMatchingCapacities(String model, Map<String, List<LineCapacity>> lineCapByModel) {
        if (model == null || model.trim().isEmpty() || lineCapByModel == null || lineCapByModel.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedModel = model.trim();
        List<LineCapacity> exactMatches = lineCapByModel.get(normalizedModel);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            return exactMatches;
        }
        List<LineCapacity> seriesMatches = new ArrayList<>();
        for (Map.Entry<String, List<LineCapacity>> entry : lineCapByModel.entrySet()) {
            if (!isSeriesMatch(normalizedModel, entry.getKey())) {
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

    private BigDecimal resolveCapacityPerHour(PlanningSnapshot.LineRuntimeView runtimeView, BigDecimal baseCapacityPerHour) {
        if (runtimeView != null && runtimeView.getCurrentCapacity() != null && runtimeView.getCurrentCapacity().compareTo(BigDecimal.ZERO) > 0) {
            return runtimeView.getCurrentCapacity();
        }
        return baseCapacityPerHour == null ? BigDecimal.ZERO : baseCapacityPerHour;
    }

    private boolean hasStatus(PlanningSnapshot.LineRuntimeView runtimeView, int status) {
        return runtimeView != null && runtimeView.getStatus() != null && runtimeView.getStatus() == status;
    }

    private String resolveRiskTag(PlanningSnapshot.LineRuntimeView runtimeView, int releasableCapacity) {
        if (releasableCapacity <= 0) {
            return "NO_CAPACITY";
        }
        if (runtimeView == null) {
            return "RUNTIME_UNKNOWN";
        }
        if (runtimeView.getStatus() != null && runtimeView.getStatus() == RuntimeStatus.IDLE) {
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
