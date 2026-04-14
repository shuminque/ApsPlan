package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.mapper.aps.ProductionLineRuntimeMapper;
import com.depository_manage.mapper.aps.ProductionPlanItemMapper;
import com.depository_manage.pojo.shift.OrderSchedulingEvaluationDTO;
import com.depository_manage.service.aps.OrderSchedulingEvaluationService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.utils.CraftMappingUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderSchedulingEvaluationServiceImpl implements OrderSchedulingEvaluationService {

    @Resource
    private ProductionLineMapper productionLineMapper;
    @Resource
    private ProductionLineModelConfigMapper modelConfigMapper;
    @Resource
    private ProductionPlanItemMapper productionPlanItemMapper;
    @Resource
    private ProductionLineRuntimeMapper productionLineRuntimeMapper;
    @Resource
    private ShiftCalendarService shiftCalendarService;

    private Clock clock = Clock.systemDefaultZone();
    private ZoneId zoneId = ZoneId.systemDefault();

    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Autowired(required = false)
    public void setZoneId(ZoneId zoneId) {
        if (zoneId != null) {
            this.zoneId = zoneId;
        }
    }

    @Override
    public OrderSchedulingEvaluationDTO evaluate(String model, String craft, Integer quantity, LocalDate deliveryDate) {
        OrderSchedulingEvaluationDTO dto = new OrderSchedulingEvaluationDTO();
        if (!StringUtils.hasText(model) || quantity == null || quantity <= 0 || deliveryDate == null) {
            return dto;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dueAt = deliveryDate.atTime(LocalTime.MAX);
        if (!dueAt.isAfter(now)) {
            dto.setStage(OrderSchedulingEvaluationDTO.Stage.DELAY_REQUIRED);
            return dto;
        }

        List<LineMatch> matches = findCandidateLines(model.trim(), craft);
        if (matches.isEmpty()) {
            dto.setStage(OrderSchedulingEvaluationDTO.Stage.DELAY_REQUIRED);
            return dto;
        }

        Map<Long, List<ProductionPlanItem>> occupiedByLine = loadOccupiedPlans(matches, now, dueAt);
        List<OrderSchedulingEvaluationDTO.LineFreeCapacityDTO> freeCapacities = new ArrayList<>();
        int totalFreeQty = 0;
        for (LineMatch match : matches) {
            long totalWindowMinutes = calcShiftWindowMinutes(now, dueAt);
            long occupiedMinutes = calcOccupiedMinutes(occupiedByLine.get(match.lineId), now, dueAt);
            long freeMinutes = Math.max(totalWindowMinutes - occupiedMinutes, 0);
            int freeQty = BigDecimal.valueOf(freeMinutes)
                    .multiply(match.capacityPerHour)
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.FLOOR)
                    .intValue();
            totalFreeQty += freeQty;

            OrderSchedulingEvaluationDTO.LineFreeCapacityDTO line = new OrderSchedulingEvaluationDTO.LineFreeCapacityDTO();
            line.setLineId(match.lineId);
            line.setLineName(match.lineName);
            line.setPriority(match.priority);
            line.setCapacityPerHour(match.capacityPerHour);
            line.setTotalWindowMinutes((int) totalWindowMinutes);
            line.setOccupiedMinutes((int) occupiedMinutes);
            line.setFreeMinutes((int) freeMinutes);
            line.setFreeQtyBeforeDue(freeQty);
            freeCapacities.add(line);
        }
        freeCapacities.sort(Comparator.comparing(OrderSchedulingEvaluationDTO.LineFreeCapacityDTO::getPriority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(OrderSchedulingEvaluationDTO.LineFreeCapacityDTO::getLineId, Comparator.nullsLast(Long::compareTo)));

        dto.setLineFreeCapacities(freeCapacities);
        dto.setFreeCapacityQtyBeforeDue(totalFreeQty);

        if (totalFreeQty < quantity) {
            List<OrderSchedulingEvaluationDTO.PreemptCandidateDTO> candidates = buildPreemptCandidates(matches, now);
            dto.setPreemptCandidates(candidates);
            int gapQty = quantity - totalFreeQty;
            int requiredCount = calcRequiredPreemptLineCount(gapQty, candidates);
            if (requiredCount > 0) {
                dto.setStage(OrderSchedulingEvaluationDTO.Stage.PREEMPT_REQUIRED);
                dto.setRequiredPreemptLineCount(requiredCount);
            } else {
                dto.setStage(OrderSchedulingEvaluationDTO.Stage.DELAY_REQUIRED);
                DelayAssessment delayAssessment = assessDelayAndRecommendLines(matches, quantity, now, deliveryDate);
                dto.setPredictedFinishTime(delayAssessment.predictedFinishTime == null ? null : delayAssessment.predictedFinishTime.toString());
                dto.setDelayDays(delayAssessment.delayDays);
                dto.setDelayMinutes(delayAssessment.delayMinutes);
                dto.setRecommendedLines(delayAssessment.recommendedLines);
            }
            return dto;
        }

        dto.setStage(OrderSchedulingEvaluationDTO.Stage.FREE_OK);
        dto.setAllocationSuggestions(buildAllocationSuggestions(quantity, now, freeCapacities));
        return dto;
    }

    private List<LineMatch> findCandidateLines(String demandModel, String craft) {
        String normalizedCraft = CraftMappingUtil.normalizeCraft(craft);
        List<ProductionLine> allLines = productionLineMapper.selectPageList(null, 0L, 2000L);
        Map<Long, ProductionLine> lineById = allLines.stream()
                .filter(l -> l.getId() != null)
                .filter(l -> l.getStatus() != null && l.getStatus() == 0)
                .filter(l -> !StringUtils.hasText(normalizedCraft) || Objects.equals(CraftMappingUtil.normalizeCraft(l.getCraft()), normalizedCraft))
                .collect(Collectors.toMap(ProductionLine::getId, l -> l, (a, b) -> a));
        if (lineById.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<ProductionLineModelConfig> configs = modelConfigMapper.selectPageList(null, null, 0L, 5000L);
        Map<Long, List<ProductionLineModelConfig>> validConfigByLine = new HashMap<>();
        for (ProductionLineModelConfig cfg : configs) {
            if (cfg == null || cfg.getLineId() == null || cfg.getCapacityPerHour() == null || !StringUtils.hasText(cfg.getModel())) {
                continue;
            }
            if (cfg.getStatus() != null && cfg.getStatus() == 0) {
                continue;
            }
            if (!lineById.containsKey(cfg.getLineId())) {
                continue;
            }
            String modelPrefix = cfg.getModel().trim();
            if (!demandModel.startsWith(modelPrefix)) {
                continue;
            }
            validConfigByLine.computeIfAbsent(cfg.getLineId(), k -> new ArrayList<>()).add(cfg);
        }

        List<LineMatch> result = new ArrayList<>();
        for (Map.Entry<Long, List<ProductionLineModelConfig>> entry : validConfigByLine.entrySet()) {
            ProductionLine line = lineById.get(entry.getKey());
            ProductionLineModelConfig best = entry.getValue().stream()
                    .sorted((left, right) -> {
                        int leftLen = left.getModel() == null ? 0 : left.getModel().trim().length();
                        int rightLen = right.getModel() == null ? 0 : right.getModel().trim().length();
                        int lenCompare = Integer.compare(rightLen, leftLen);
                        if (lenCompare != 0) {
                            return lenCompare;
                        }
                        int leftPriority = left.getPriority() == null ? Integer.MAX_VALUE : left.getPriority();
                        int rightPriority = right.getPriority() == null ? Integer.MAX_VALUE : right.getPriority();
                        int priorityCompare = Integer.compare(leftPriority, rightPriority);
                        if (priorityCompare != 0) {
                            return priorityCompare;
                        }
                        BigDecimal leftCap = left.getCapacityPerHour() == null ? BigDecimal.ZERO : left.getCapacityPerHour();
                        BigDecimal rightCap = right.getCapacityPerHour() == null ? BigDecimal.ZERO : right.getCapacityPerHour();
                        return rightCap.compareTo(leftCap);
                    })
                    .findFirst()
                    .orElse(null);
            if (best == null) {
                continue;
            }
            result.add(new LineMatch(line.getId(), line.getLineName(), best.getPriority(), best.getCapacityPerHour()));
        }
        result.sort(Comparator.comparing(LineMatch::priority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(LineMatch::lineId));
        return result;
    }

    private Map<Long, List<ProductionPlanItem>> loadOccupiedPlans(List<LineMatch> matches,
                                                                  LocalDateTime now,
                                                                  LocalDateTime dueAt) {
        Set<Long> lineIds = matches.stream().map(LineMatch::lineId).collect(Collectors.toSet());
        List<ProductionPlanItem> items = productionPlanItemMapper.selectList(new LambdaQueryWrapper<ProductionPlanItem>()
                .in(ProductionPlanItem::getLineId, lineIds)
                .le(ProductionPlanItem::getStartDate, toDate(dueAt))
                .ge(ProductionPlanItem::getEndDate, toDate(now)));
        return items.stream().collect(Collectors.groupingBy(ProductionPlanItem::getLineId));
    }

    private long calcShiftWindowMinutes(LocalDateTime startAt, LocalDateTime dueAt) {
        Set<Long> uniqueScheduleIds = new HashSet<>();
        List<TimeRange> ranges = new ArrayList<>();
        LocalDate cursor = startAt.toLocalDate();
        while (!cursor.isAfter(dueAt.toLocalDate())) {
            List<ShiftSchedule> schedules = new ArrayList<>();
            schedules.addAll(shiftCalendarService.getSchedulesByDate(cursor.toString()));
            schedules.addAll(shiftCalendarService.getSchedulesByDate(cursor.minusDays(1).toString()));
            for (ShiftSchedule schedule : schedules) {
                if (schedule == null || schedule.getStartDateTime() == null || schedule.getEndDateTime() == null) {
                    continue;
                }
                if (schedule.getScheduleId() != null && !uniqueScheduleIds.add(schedule.getScheduleId())) {
                    continue;
                }
                TimeRange range = overlap(toLocalDateTime(schedule.getStartDateTime()), toLocalDateTime(schedule.getEndDateTime()), startAt, dueAt);
                if (range != null) {
                    ranges.add(range);
                }
            }
            cursor = cursor.plusDays(1);
        }
        return mergeMinutes(ranges);
    }

    private long calcOccupiedMinutes(List<ProductionPlanItem> items,
                                     LocalDateTime startAt,
                                     LocalDateTime dueAt) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        List<TimeRange> ranges = new ArrayList<>();
        for (ProductionPlanItem item : items) {
            if (item == null || item.getStartDate() == null || item.getEndDate() == null) {
                continue;
            }
            TimeRange overlap = overlap(toLocalDateTime(item.getStartDate()), toLocalDateTime(item.getEndDate()), startAt, dueAt);
            if (overlap != null) {
                ranges.add(overlap);
            }
        }
        return mergeMinutes(ranges);
    }

    private List<OrderSchedulingEvaluationDTO.AllocationSuggestionDTO> buildAllocationSuggestions(Integer orderQty,
                                                                                                   LocalDateTime now,
                                                                                                   List<OrderSchedulingEvaluationDTO.LineFreeCapacityDTO> lines) {
        int remaining = orderQty == null ? 0 : Math.max(orderQty, 0);
        List<OrderSchedulingEvaluationDTO.AllocationSuggestionDTO> suggestions = new ArrayList<>();
        for (OrderSchedulingEvaluationDTO.LineFreeCapacityDTO line : lines) {
            if (remaining <= 0) {
                break;
            }
            int alloc = Math.min(remaining, Math.max(line.getFreeQtyBeforeDue(), 0));
            if (alloc <= 0) {
                continue;
            }
            long minutesNeeded = BigDecimal.valueOf(alloc)
                    .multiply(BigDecimal.valueOf(60))
                    .divide(line.getCapacityPerHour(), 0, RoundingMode.CEILING)
                    .longValue();
            OrderSchedulingEvaluationDTO.AllocationSuggestionDTO suggestion = new OrderSchedulingEvaluationDTO.AllocationSuggestionDTO();
            suggestion.setLineId(line.getLineId());
            suggestion.setLineName(line.getLineName());
            suggestion.setAllocatedQty(alloc);
            suggestion.setEstimatedFinishTime(now.plusMinutes(minutesNeeded).toString());
            suggestions.add(suggestion);
            remaining -= alloc;
        }
        suggestions.sort(Comparator.comparing(OrderSchedulingEvaluationDTO.AllocationSuggestionDTO::getEstimatedFinishTime,
                Comparator.nullsLast(String::compareTo))
                .thenComparing(OrderSchedulingEvaluationDTO.AllocationSuggestionDTO::getLineId, Comparator.nullsLast(Long::compareTo)));
        return suggestions;
    }

    private List<OrderSchedulingEvaluationDTO.PreemptCandidateDTO> buildPreemptCandidates(List<LineMatch> matches,
                                                                                           LocalDateTime now) {
        Map<Long, BigDecimal> capacityByLine = matches.stream()
                .collect(Collectors.toMap(LineMatch::lineId,
                        lineMatch -> lineMatch.capacityPerHour == null ? BigDecimal.ZERO : lineMatch.capacityPerHour,
                        (a, b) -> a));
        List<com.depository_manage.entity.aps.ProductionLineRuntime> runningLines = productionLineRuntimeMapper.selectList(null,null).stream()
                .filter(Objects::nonNull)
                .filter(runtime -> runtime.getStatus() != null && runtime.getStatus() == 1)
                .collect(Collectors.toList());
        if (runningLines.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> lineIds = runningLines.stream()
                .map(com.depository_manage.entity.aps.ProductionLineRuntime::getLineId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (lineIds.isEmpty()) {
            return new ArrayList<>();
        }
        Date nowDate = toDate(now);
        List<ProductionPlanItem> runningItems = productionPlanItemMapper.selectList(new LambdaQueryWrapper<ProductionPlanItem>()
                .in(ProductionPlanItem::getLineId, lineIds)
                .le(ProductionPlanItem::getStartDate, nowDate)
                .ge(ProductionPlanItem::getEndDate, nowDate));
        Map<Long, ProductionPlanItem> currentByLine = runningItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getLineId() != null)
                .collect(Collectors.toMap(ProductionPlanItem::getLineId, item -> item, (left, right) -> {
                    Date leftStart = left.getStartDate();
                    Date rightStart = right.getStartDate();
                    if (leftStart == null) {
                        return right;
                    }
                    if (rightStart == null) {
                        return left;
                    }
                    return rightStart.after(leftStart) ? right : left;
                }));

        List<OrderSchedulingEvaluationDTO.PreemptCandidateDTO> candidates = new ArrayList<>();
        for (com.depository_manage.entity.aps.ProductionLineRuntime runtime : runningLines) {
            Long lineId = runtime.getLineId();
            ProductionPlanItem item = currentByLine.get(lineId);
            if (item == null || item.getStartDate() == null || item.getAssignQty() == null || item.getAssignQty() <= 0) {
                continue;
            }
            BigDecimal capacityPerHour = runtime.getCurrentCapacity() != null ? runtime.getCurrentCapacity() : capacityByLine.get(lineId);
            if (capacityPerHour == null || capacityPerHour.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDateTime startedAt = toLocalDateTime(item.getStartDate());
            LocalDateTime activeUntil = now;
            if (item.getEndDate() != null) {
                LocalDateTime endedAt = toLocalDateTime(item.getEndDate());
                if (endedAt.isBefore(activeUntil)) {
                    activeUntil = endedAt;
                }
            }
            if (!activeUntil.isAfter(startedAt)) {
                continue;
            }
            long effectiveMinutes = calcShiftWindowMinutes(startedAt, activeUntil);
            int estimatedOutput = BigDecimal.valueOf(effectiveMinutes)
                    .multiply(capacityPerHour)
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.FLOOR)
                    .intValue();
            estimatedOutput = Math.min(estimatedOutput, item.getAssignQty());
            int orderDemandQty = item.getOrderDemandQty() == null ? 0 : Math.max(item.getOrderDemandQty(), 0);
            if (!(estimatedOutput >= orderDemandQty && estimatedOutput < item.getAssignQty())) {
                continue;
            }
            int preemptableQty = item.getAssignQty() - Math.max(estimatedOutput, orderDemandQty);
            if (preemptableQty <= 0) {
                continue;
            }
            int impactDelayMinutes = BigDecimal.valueOf(preemptableQty)
                    .multiply(BigDecimal.valueOf(60))
                    .divide(capacityPerHour, 0, RoundingMode.CEILING)
                    .intValue();

            OrderSchedulingEvaluationDTO.PreemptCandidateDTO candidate = new OrderSchedulingEvaluationDTO.PreemptCandidateDTO();
            candidate.setLineId(lineId);
            candidate.setLineName(runtime.getLineName());
            candidate.setPlanItemId(item.getId());
            candidate.setModel(item.getModel());
            candidate.setAssignQty(item.getAssignQty());
            candidate.setOrderDemandQty(orderDemandQty);
            candidate.setEstimatedOutput(estimatedOutput);
            candidate.setReleasableCapacityQty(preemptableQty);
            candidate.setPreemptableQty(preemptableQty);
            candidate.setImpactDelayMinutes(Math.max(impactDelayMinutes, 0));
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparing(OrderSchedulingEvaluationDTO.PreemptCandidateDTO::getReleasableCapacityQty,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(OrderSchedulingEvaluationDTO.PreemptCandidateDTO::getImpactDelayMinutes,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(OrderSchedulingEvaluationDTO.PreemptCandidateDTO::getLineId,
                        Comparator.nullsLast(Long::compareTo)));
        return candidates;
    }

    private int calcRequiredPreemptLineCount(int gapQty, List<OrderSchedulingEvaluationDTO.PreemptCandidateDTO> candidates) {
        if (gapQty <= 0) {
            return 0;
        }
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        List<OrderSchedulingEvaluationDTO.PreemptCandidateDTO> sortedCandidates = candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(OrderSchedulingEvaluationDTO.PreemptCandidateDTO::getReleasableCapacityQty,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(OrderSchedulingEvaluationDTO.PreemptCandidateDTO::getImpactDelayMinutes,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(OrderSchedulingEvaluationDTO.PreemptCandidateDTO::getLineId,
                                Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
        int remaining = Math.max(gapQty, 0);
        int used = 0;
        for (OrderSchedulingEvaluationDTO.PreemptCandidateDTO candidate : sortedCandidates) {
            if (remaining <= 0) {
                break;
            }
            int qty = candidate.getReleasableCapacityQty() == null ? 0 : Math.max(candidate.getReleasableCapacityQty(), 0);
            if (qty <= 0) {
                continue;
            }
            remaining -= qty;
            used++;
        }
        if (remaining > 0) {
            return 0;
        }
        return Math.max(used, 0);
    }

    private DelayAssessment assessDelayAndRecommendLines(List<LineMatch> matches,
                                                         Integer quantity,
                                                         LocalDateTime now,
                                                         LocalDate deliveryDate) {
        if (matches == null || matches.isEmpty() || quantity == null || quantity <= 0) {
            return new DelayAssessment(null, 0, 0, new ArrayList<>());
        }
        Set<Long> lineIds = matches.stream()
                .map(LineMatch::lineId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<ProductionPlanItem> futurePlans = lineIds.isEmpty() ? new ArrayList<>() : productionPlanItemMapper.selectList(
                new LambdaQueryWrapper<ProductionPlanItem>()
                        .in(ProductionPlanItem::getLineId, lineIds)
                        .ge(ProductionPlanItem::getEndDate, toDate(now))
        );
        Map<Long, List<ProductionPlanItem>> plansByLine = futurePlans.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getLineId() != null)
                .collect(Collectors.groupingBy(ProductionPlanItem::getLineId));

        List<LineQueueCandidate> queueCandidates = new ArrayList<>();
        for (LineMatch match : matches) {
            if (match.capacityPerHour == null || match.capacityPerHour.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDateTime earliestStart = computeEarliestQueueStart(now, plansByLine.get(match.lineId));
            queueCandidates.add(new LineQueueCandidate(match.lineId, match.lineName, match.capacityPerHour, earliestStart));
        }
        if (queueCandidates.isEmpty()) {
            return new DelayAssessment(null, 0, 0, new ArrayList<>());
        }

        LocalDateTime predictedFinish = solveEarliestJointFinish(queueCandidates, quantity);
        Map<Long, Integer> allocation = allocateByFinishTime(queueCandidates, predictedFinish, quantity);
        List<OrderSchedulingEvaluationDTO.DelayRecommendationLineDTO> recommendedLines = queueCandidates.stream()
                .filter(line -> allocation.getOrDefault(line.lineId, 0) > 0)
                .map(line -> {
                    int qty = allocation.getOrDefault(line.lineId, 0);
                    LocalDateTime lineFinish = estimateFinish(line.earliestStart, line.capacityPerHour, qty);
                    OrderSchedulingEvaluationDTO.DelayRecommendationLineDTO dto = new OrderSchedulingEvaluationDTO.DelayRecommendationLineDTO();
                    dto.setLineId(line.lineId);
                    dto.setLineName(line.lineName);
                    dto.setEarliestStartTime(line.earliestStart.toString());
                    dto.setEarliestFinishTime(lineFinish == null ? null : lineFinish.toString());
                    dto.setRecommendedQty(qty);
                    return dto;
                })
                .sorted(Comparator.comparing(OrderSchedulingEvaluationDTO.DelayRecommendationLineDTO::getEarliestFinishTime,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(OrderSchedulingEvaluationDTO.DelayRecommendationLineDTO::getLineId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
        int delayDays = 0;
        int delayMinutes = 0;
        if (predictedFinish != null && deliveryDate != null && predictedFinish.toLocalDate().isAfter(deliveryDate)) {
            delayDays = (int) ChronoUnit.DAYS.between(deliveryDate, predictedFinish.toLocalDate());
            LocalDateTime deliveryDeadline = deliveryDate.plusDays(1).atStartOfDay();
            delayMinutes = (int) Math.max(ChronoUnit.MINUTES.between(deliveryDeadline, predictedFinish), 0);
        }
        return new DelayAssessment(predictedFinish, Math.max(delayDays, 0), Math.max(delayMinutes, 0), recommendedLines);
    }

    private LocalDateTime computeEarliestQueueStart(LocalDateTime now, List<ProductionPlanItem> items) {
        if (items == null || items.isEmpty()) {
            return now;
        }
        List<TimeRange> ranges = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getStartDate() != null && item.getEndDate() != null)
                .map(item -> new TimeRange(toLocalDateTime(item.getStartDate()), toLocalDateTime(item.getEndDate())))
                .filter(range -> range.end.isAfter(now))
                .sorted(Comparator.comparing(TimeRange::start))
                .collect(Collectors.toList());
        LocalDateTime cursor = now;
        boolean moved;
        do {
            moved = false;
            for (TimeRange range : ranges) {
                if (!range.end.isAfter(cursor)) {
                    continue;
                }
                if (!range.start.isAfter(cursor)) {
                    cursor = range.end;
                    moved = true;
                }
            }
        } while (moved);
        return cursor;
    }

    private LocalDateTime solveEarliestJointFinish(List<LineQueueCandidate> lines, int totalQty) {
        LocalDateTime lower = lines.stream()
                .map(LineQueueCandidate::earliestStart)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now(clock));
        LocalDateTime upper = lower.plusHours(1);
        while (producibleQtyUntil(lines, upper) < totalQty) {
            upper = upper.plusHours(12);
        }
        while (ChronoUnit.MINUTES.between(lower, upper) > 1) {
            long halfMinutes = ChronoUnit.MINUTES.between(lower, upper) / 2;
            LocalDateTime mid = lower.plusMinutes(halfMinutes);
            if (producibleQtyUntil(lines, mid) >= totalQty) {
                upper = mid;
            } else {
                lower = mid;
            }
        }
        return upper;
    }

    private int producibleQtyUntil(List<LineQueueCandidate> lines, LocalDateTime until) {
        int total = 0;
        for (LineQueueCandidate line : lines) {
            if (!until.isAfter(line.earliestStart)) {
                continue;
            }
            long minutes = ChronoUnit.MINUTES.between(line.earliestStart, until);
            int qty = BigDecimal.valueOf(minutes)
                    .multiply(line.capacityPerHour)
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.FLOOR)
                    .intValue();
            total += Math.max(qty, 0);
        }
        return total;
    }

    private Map<Long, Integer> allocateByFinishTime(List<LineQueueCandidate> lines, LocalDateTime finishAt, int totalQty) {
        Map<Long, Integer> allocation = new HashMap<>();
        int assigned = 0;
        for (LineQueueCandidate line : lines) {
            if (!finishAt.isAfter(line.earliestStart)) {
                allocation.put(line.lineId, 0);
                continue;
            }
            long minutes = ChronoUnit.MINUTES.between(line.earliestStart, finishAt);
            int qty = BigDecimal.valueOf(minutes)
                    .multiply(line.capacityPerHour)
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.FLOOR)
                    .intValue();
            qty = Math.max(qty, 0);
            allocation.put(line.lineId, qty);
            assigned += qty;
        }
        int overflow = assigned - Math.max(totalQty, 0);
        if (overflow <= 0) {
            return allocation;
        }
        List<LineQueueCandidate> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(LineQueueCandidate::earliestStart).reversed()
                .thenComparing(LineQueueCandidate::lineId, Comparator.nullsLast(Long::compareTo)));
        for (LineQueueCandidate line : sorted) {
            if (overflow <= 0) {
                break;
            }
            int current = allocation.getOrDefault(line.lineId, 0);
            int reduce = Math.min(current, overflow);
            allocation.put(line.lineId, current - reduce);
            overflow -= reduce;
        }
        return allocation;
    }

    private LocalDateTime estimateFinish(LocalDateTime start, BigDecimal capacityPerHour, int qty) {
        if (start == null || capacityPerHour == null || capacityPerHour.compareTo(BigDecimal.ZERO) <= 0 || qty <= 0) {
            return start;
        }
        long minutes = BigDecimal.valueOf(qty)
                .multiply(BigDecimal.valueOf(60))
                .divide(capacityPerHour, 0, RoundingMode.CEILING)
                .longValue();
        return start.plusMinutes(minutes);
    }

    private TimeRange overlap(LocalDateTime rangeStart,
                              LocalDateTime rangeEnd,
                              LocalDateTime windowStart,
                              LocalDateTime windowEnd) {
        if (rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
            return null;
        }
        LocalDateTime start = rangeStart.isBefore(windowStart) ? windowStart : rangeStart;
        LocalDateTime end = rangeEnd.isAfter(windowEnd) ? windowEnd : rangeEnd;
        if (!end.isAfter(start)) {
            return null;
        }
        return new TimeRange(start, end);
    }

    private long mergeMinutes(List<TimeRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return 0;
        }
        ranges.sort(Comparator.comparing(TimeRange::start));
        long minutes = 0;
        LocalDateTime mergedStart = ranges.get(0).start;
        LocalDateTime mergedEnd = ranges.get(0).end;
        for (int i = 1; i < ranges.size(); i++) {
            TimeRange current = ranges.get(i);
            if (!current.start.isAfter(mergedEnd)) {
                if (current.end.isAfter(mergedEnd)) {
                    mergedEnd = current.end;
                }
                continue;
            }
            minutes += ChronoUnit.MINUTES.between(mergedStart, mergedEnd);
            mergedStart = current.start;
            mergedEnd = current.end;
        }
        minutes += ChronoUnit.MINUTES.between(mergedStart, mergedEnd);
        return Math.max(minutes, 0);
    }

    private Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(zoneId).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(zoneId).toLocalDateTime();
    }

    private static final class LineMatch {
        private final Long lineId;
        private final String lineName;
        private final Integer priority;
        private final BigDecimal capacityPerHour;

        private LineMatch(Long lineId, String lineName, Integer priority, BigDecimal capacityPerHour) {
            this.lineId = lineId;
            this.lineName = lineName;
            this.priority = priority;
            this.capacityPerHour = capacityPerHour;
        }

        private Long lineId() {
            return lineId;
        }

        private Integer priority() {
            return priority;
        }
    }

    private static final class TimeRange {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        private LocalDateTime start() {
            return start;
        }
    }

    private static final class LineQueueCandidate {
        private final Long lineId;
        private final String lineName;
        private final BigDecimal capacityPerHour;
        private final LocalDateTime earliestStart;

        private LineQueueCandidate(Long lineId, String lineName, BigDecimal capacityPerHour, LocalDateTime earliestStart) {
            this.lineId = lineId;
            this.lineName = lineName;
            this.capacityPerHour = capacityPerHour;
            this.earliestStart = earliestStart;
        }

        private LocalDateTime earliestStart() {
            return earliestStart;
        }

        private Long lineId() {
            return lineId;
        }
    }

    private static final class DelayAssessment {
        private final LocalDateTime predictedFinishTime;
        private final int delayDays;
        private final int delayMinutes;
        private final List<OrderSchedulingEvaluationDTO.DelayRecommendationLineDTO> recommendedLines;

        private DelayAssessment(LocalDateTime predictedFinishTime,
                                int delayDays,
                                int delayMinutes,
                                List<OrderSchedulingEvaluationDTO.DelayRecommendationLineDTO> recommendedLines) {
            this.predictedFinishTime = predictedFinishTime;
            this.delayDays = delayDays;
            this.delayMinutes = delayMinutes;
            this.recommendedLines = recommendedLines;
        }
    }
}
