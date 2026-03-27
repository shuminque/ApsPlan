package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.SafetyStock;
import com.depository_manage.service.aps.planning.NormalizedPlanningRequest;
import com.depository_manage.utils.CraftMappingUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PlanningEngineV1 implements PlanningEngine {

    private static final String OBJECTIVE_MIN_LINE = "min_line";
    private static final BigDecimal DAILY_TARGET_BUFFER = new BigDecimal("1.05");

    @Override
    public PlanningResult plan(PlanningContext context) {
        NormalizedPlanningRequest normalizedRequest = context.getNormalizedRequest();
        LocalDate requestStart = normalizedRequest.getRequestStart();
        LocalDate start = normalizedRequest.getStart();
        LocalDate endExclusive = normalizedRequest.getEndExclusive();
        if (requestStart == null || endExclusive == null) {
            return PlanningEngineSupport.emptyResult();
        }

        String normalizedMode = normalizedRequest.getMode();
        String normalizedObjective = normalizedRequest.getObjective();
        Set<Long> insertOrderIdSet = normalizedRequest.getInsertOrderIds();
        Map<Long, LocalDateTime> orderStartTimes = normalizedRequest.getOrderStartTimes();
        PlanningSnapshot snapshot = context.getSnapshot();

        if (snapshot.getOpenOrders().isEmpty()) {
            return PlanningEngineSupport.emptyResult();
        }

        Clock clock = context.getClock();
        ZoneId zoneId = context.getZoneId();
        LocalDateTime effectiveStartAt = normalizedRequest.getEffectiveStartAt();
        List<DemandItem> demands = buildDemands(snapshot.getOrderByKey(), snapshot.getSafetyStockByKey(),
                snapshot.getCurrentInventoryByKey(), normalizedMode, insertOrderIdSet,
                normalizeOrderStartTimes(orderStartTimes), effectiveStartAt, clock, zoneId);
        if (demands.isEmpty()) {
            return PlanningEngineSupport.emptyResult();
        }

        LocalDate latestDeliveryExclusive = demands.stream()
                .map(DemandItem::earliestDeliveryDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .map(d -> d.plusDays(1))
                .orElse(endExclusive);
        if (endExclusive.isBefore(latestDeliveryExclusive)) {
            endExclusive = latestDeliveryExclusive;
        }

        Map<LocalDate, BigDecimal> shiftHoursByDay = snapshot.getShiftHoursByDay();
        Map<String, List<LineCapacity>> lineCapByModel = snapshot.getLineCapByModel();
        Map<LineDayKey, Integer> remainingCapacityByLineDay = snapshot.getRemainingCapacityByLineDay();
        LocalDate planningCapacityEndExclusive = shiftHoursByDay.keySet().stream()
                .max(LocalDate::compareTo)
                .map(day -> day.plusDays(1))
                .orElse(endExclusive);
        Map<DemandItem, DemandLineMatch> barLineMatchesByDemand = buildBarLineMatchesByDemand(demands, lineCapByModel);
        List<RingPairDemand> ringPairDemands = buildRingPairDemands(demands, barLineMatchesByDemand);
        Map<String, LineActivationPlan> activationPlanByKey = new HashMap<String, LineActivationPlan>();

        List<PlanSlice> plannedSlices = new ArrayList<PlanSlice>();
        LocalDate cursor = start;
        while (cursor.isBefore(planningCapacityEndExclusive)) {
            if (allDemandsCompleted(demands)) {
                break;
            }
            final LocalDate day = cursor;
            schedulePairedBarDemands(day, planningCapacityEndExclusive, ringPairDemands, remainingCapacityByLineDay,
                    plannedSlices, activationPlanByKey, normalizedObjective);
            for (DemandItem demand : demands) {
                if (demand.remaining() <= 0 || !demand.canScheduleOn(day)) {
                    continue;
                }
                List<LineCapacity> lines = findMatchingLines(demand.model(), lineCapByModel).stream()
                        .filter(line -> line.matchesCraft(demand.requiredCraft()))
                        .collect(Collectors.toList());
                List<LineCapacity> prioritizedLines = prioritizeCandidateLines(demand.activationKey(), lines, day, planningCapacityEndExclusive,
                        demand.remaining(), remainingCapacityByLineDay, activationPlanByKey, normalizedObjective);
                assignDemandToLines(day, demand, prioritizedLines, remainingCapacityByLineDay,
                        (line, assignQty) -> plannedSlices.add(new PlanSlice(day, line.lineId, line.lineName,
                                demand.customer(), demand.outerInnerRing(), demand.model(), assignQty, line.capacityPerHour)));
            }
            if (allDemandsCompleted(demands)) {
                break;
            }
            cursor = cursor.plusDays(1);
        }
        LocalDateTime actualStart = resolveActualPlanStart(plannedSlices, effectiveStartAt, start);
        LocalDateTime actualEnd = resolveActualPlanEnd(plannedSlices, actualStart);
        PlanningResult.Metrics metrics = PlanningEngineSupport.calculateMetrics(demands, endExclusive);
        return new PlanningResult(plannedSlices, demands, actualStart, actualEnd, metrics, null);
    }

    @Override
    public String version() {
        return "v1";
    }

    List<DemandItem> buildDemands(Map<String, List<ProductionOrder>> orderByKey,
                                  Map<String, SafetyStock> safetyStockByKey,
                                  Map<String, Integer> currentInventoryByKey,
                                  String planMode,
                                  Set<Long> insertOrderIdSet,
                                  Map<Long, LocalDateTime> orderStartTimes,
                                  LocalDateTime defaultStartAt,
                                  Clock clock,
                                  ZoneId zoneId) {
        List<DemandItem> result = new ArrayList<DemandItem>();
        boolean insertMode = "INSERT".equals(planMode) && !insertOrderIdSet.isEmpty();
        for (Map.Entry<String, List<ProductionOrder>> entry : orderByKey.entrySet()) {
            List<ProductionOrder> groupOrders = entry.getValue();
            if (groupOrders.isEmpty()) {
                continue;
            }
            ProductionOrder any = groupOrders.get(0);
            int orderCount = groupOrders.size();
            int orderDemandQuantity = groupOrders.stream().mapToInt(this::remainingOrderQuantity).sum();
            int currentInventory = currentInventoryByKey.getOrDefault(entry.getKey(), 0);
            SafetyStock stock = safetyStockByKey.get(entry.getKey());
            BigDecimal safetyTarget = BigDecimal.ZERO;
            if (stock != null && stock.getStockCycle() != null && stock.getMonthlyStockQty() != null) {
                safetyTarget = stock.getStockCycle().multiply(stock.getMonthlyStockQty());
            }
            int safetyTargetQty = safetyTarget.setScale(0, RoundingMode.HALF_UP).intValue();

            List<ProductionOrder> sortedOrders = new ArrayList<ProductionOrder>(groupOrders);
            sortedOrders.sort(Comparator
                    .comparing(ProductionOrder::getDeliveryDate, Comparator.nullsLast(Date::compareTo))
                    .thenComparing((ProductionOrder o) -> parseOrderPriority(o.getPriority()), Comparator.reverseOrder())
                    .thenComparing(ProductionOrder::getId, Comparator.nullsLast(Long::compareTo)));

            List<OrderDemandSeed> orderDemandSeeds = new ArrayList<OrderDemandSeed>();
            for (ProductionOrder order : sortedOrders) {
                int remainingQty = remainingOrderQuantity(order);
                if (remainingQty <= 0) {
                    continue;
                }
                orderDemandSeeds.add(new OrderDemandSeed(order, remainingQty));
            }

            int inventoryPool = Math.max(currentInventory, 0);
            int safetyRequired = Math.max(safetyTargetQty - inventoryPool, 0);
            inventoryPool = Math.max(0, inventoryPool - safetyTargetQty);

            for (OrderDemandSeed seed : orderDemandSeeds) {
                int coveredByInventory = Math.min(inventoryPool, seed.requiredQty);
                inventoryPool -= coveredByInventory;
                int required = seed.requiredQty - coveredByInventory;
                if (required <= 0) {
                    continue;
                }
                ProductionOrder order = seed.order;
                LocalDateTime orderStartAt = resolveDemandStartAt(order.getId(), orderStartTimes, defaultStartAt);
                int priority = parseOrderPriority(order.getPriority());
                boolean locked = insertMode && order.getId() != null && insertOrderIdSet.contains(order.getId());
                result.add(new DemandItem(order.getId(), order.getCustomer(), order.getOuterInnerRing(), order.getModel(), order.getCraft(), required,
                        currentInventory, orderCount, orderDemandQuantity, safetyTargetQty, order.getDeliveryDate(),
                        Math.max(priority, locked ? 2 : priority), locked, orderStartAt, LocalDate.now(clock), zoneId));
            }

            if (safetyRequired > 0) {
                Date earliestDelivery = sortedOrders.stream()
                        .map(ProductionOrder::getDeliveryDate)
                        .filter(Objects::nonNull)
                        .min(Date::compareTo)
                        .orElse(null);
                int groupPriority = sortedOrders.stream()
                        .map(ProductionOrder::getPriority)
                        .mapToInt(this::parseOrderPriority)
                        .max()
                        .orElse(0);
                result.add(new DemandItem(null, any.getCustomer(), any.getOuterInnerRing(), any.getModel(), any.getCraft(), safetyRequired,
                        currentInventory, orderCount, orderDemandQuantity, safetyTargetQty, earliestDelivery, groupPriority, false,
                        normalizePlanStart(defaultStartAt), LocalDate.now(clock), zoneId));
            }
        }

        result.sort(Comparator
                .comparing((DemandItem d) -> d.lockedInsert()).reversed()
                .thenComparing(Comparator.comparingInt((DemandItem d) -> d.priority).reversed())
                .thenComparing(DemandItem::earliestStartDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparingInt(DemandItem::deliveryUrgencyDays)
                .thenComparing((DemandItem d) -> d.required(), Comparator.reverseOrder()));
        return result;
    }

    Map<DemandItem, DemandLineMatch> buildBarLineMatchesByDemand(List<DemandItem> demands,
                                                                  Map<String, List<LineCapacity>> lineCapByModel) {
        Map<DemandItem, DemandLineMatch> result = new HashMap<DemandItem, DemandLineMatch>();
        for (DemandItem demand : demands) {
            Map<String, List<LineCapacity>> barLinesBySeries = new HashMap<String, List<LineCapacity>>();
            if (!CraftMappingUtil.BAR_CRAFT.equals(demand.requiredCraft())) {
                result.put(demand, new DemandLineMatch(barLinesBySeries));
                continue;
            }
            for (Map.Entry<String, List<LineCapacity>> entry : lineCapByModel.entrySet()) {
                if (!isSeriesMatch(demand.model(), entry.getKey())) {
                    continue;
                }
                List<LineCapacity> barLines = entry.getValue().stream()
                        .filter(LineCapacity::isBarCraft)
                        .collect(Collectors.toList());
                if (barLines.isEmpty()) {
                    continue;
                }
                sortLineCapacities(barLines);
                barLinesBySeries.put(entry.getKey(), barLines);
            }
            result.put(demand, new DemandLineMatch(barLinesBySeries));
        }
        return result;
    }

    List<RingPairDemand> buildRingPairDemands(List<DemandItem> demands,
                                               Map<DemandItem, DemandLineMatch> barLineMatchesByDemand) {
        Map<String, List<DemandItem>> laDemandByCustomer = demands.stream()
                .filter(demand -> "LA".equalsIgnoreCase(demand.outerInnerRing()))
                .collect(Collectors.groupingBy(DemandItem::normalizedCustomer));
        Map<String, List<DemandItem>> lbDemandByCustomer = demands.stream()
                .filter(demand -> "LB".equalsIgnoreCase(demand.outerInnerRing()))
                .collect(Collectors.groupingBy(DemandItem::normalizedCustomer));
        List<RingPairDemand> pairDemands = new ArrayList<RingPairDemand>();
        for (Map.Entry<String, List<DemandItem>> entry : laDemandByCustomer.entrySet()) {
            List<DemandItem> laDemands = entry.getValue();
            List<DemandItem> lbDemands = lbDemandByCustomer.getOrDefault(entry.getKey(), Collections.emptyList());
            if (lbDemands.isEmpty()) {
                continue;
            }
            Set<DemandItem> matchedLbDemands = new HashSet<DemandItem>();
            for (DemandItem laDemand : laDemands) {
                PairCandidate bestCandidate = null;
                for (DemandItem lbDemand : lbDemands) {
                    if (matchedLbDemands.contains(lbDemand)) {
                        continue;
                    }
                    DemandLineMatch laMatch = barLineMatchesByDemand.get(laDemand);
                    DemandLineMatch lbMatch = barLineMatchesByDemand.get(lbDemand);
                    String sharedSeries = findBestSharedSeries(laMatch, lbMatch);
                    if (sharedSeries == null) {
                        continue;
                    }
                    List<LineCapacity> sharedBarLines = findSharedBarLines(laMatch, lbMatch, sharedSeries);
                    if (sharedBarLines.isEmpty()) {
                        continue;
                    }
                    PairCandidate candidate = new PairCandidate(lbDemand, sharedSeries, sharedBarLines);
                    if (bestCandidate == null || comparePairCandidate(candidate, bestCandidate) < 0) {
                        bestCandidate = candidate;
                    }
                }
                if (bestCandidate == null) {
                    continue;
                }
                matchedLbDemands.add(bestCandidate.lbDemand);
                pairDemands.add(new RingPairDemand(laDemand, bestCandidate.lbDemand, bestCandidate.sharedSeries, bestCandidate.sharedBarLines));
            }
        }

        pairDemands.sort(Comparator
                .comparing((RingPairDemand d) -> d.lockedInsert()).reversed()
                .thenComparing(Comparator.comparingInt((RingPairDemand d) -> d.priority).reversed())
                .thenComparingInt(RingPairDemand::deliveryUrgencyDays)
                .thenComparing(RingPairDemand::maxRequired, Comparator.reverseOrder()));
        return pairDemands;
    }

    List<LineCapacity> prioritizeCandidateLines(String activationKey,
                                                List<LineCapacity> lines,
                                                LocalDate day,
                                                LocalDate endExclusive,
                                                int demandRemaining,
                                                Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                                Map<String, LineActivationPlan> activationPlanByKey,
                                                String objective) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, LineCapacity> uniqueLines = new HashMap<Long, LineCapacity>();
        for (LineCapacity line : lines) {
            uniqueLines.merge(line.lineId, line, this::pickHigherCapacityLine);
        }

        List<LineCapacity> candidates = uniqueLines.values().stream()
                .sorted((left, right) -> compareByRemainingHorizonCapacity(left, right, day, endExclusive, remainingCapacityByLineDay))
                .collect(Collectors.toList());

        LineActivationPlan activationPlan = activationPlanByKey.computeIfAbsent(activationKey, key -> new LineActivationPlan());
        activationPlan.ensureMinimumLines(candidates, day, endExclusive, demandRemaining, remainingCapacityByLineDay, objective);

        List<LineCapacity> availableLines = activationPlan.activatedLines(candidates, day, endExclusive, demandRemaining,
                remainingCapacityByLineDay, objective);
        if (!availableLines.isEmpty()) {
            return availableLines;
        }

        List<LineCapacity> fallbackLines = candidates.stream()
                .filter(line -> remainingCapacityByLineDay.getOrDefault(new LineDayKey(line.lineId, day), 0) > 0)
                .collect(Collectors.toList());
        if (fallbackLines.isEmpty()) {
            return Collections.emptyList();
        }
        activationPlan.ensureMinimumLines(fallbackLines, day, endExclusive, demandRemaining, remainingCapacityByLineDay, objective);
        return activationPlan.activatedLines(fallbackLines, day, endExclusive, demandRemaining, remainingCapacityByLineDay, objective);
    }

    List<LineCapacity> findMatchingLines(String demandModel, Map<String, List<LineCapacity>> lineCapByModel) {
        if (demandModel == null || demandModel.trim().isEmpty() || lineCapByModel == null || lineCapByModel.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedDemandModel = demandModel.trim();
        List<LineCapacity> exactMatches = lineCapByModel.get(normalizedDemandModel);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            List<LineCapacity> sortedExactMatches = new ArrayList<LineCapacity>(exactMatches);
            sortLineCapacities(sortedExactMatches);
            return sortedExactMatches;
        }

        List<LineCapacity> seriesMatches = new ArrayList<LineCapacity>();
        for (Map.Entry<String, List<LineCapacity>> entry : lineCapByModel.entrySet()) {
            String configModel = entry.getKey();
            if (!isSeriesMatch(normalizedDemandModel, configModel)) {
                continue;
            }
            seriesMatches.addAll(entry.getValue());
        }
        sortLineCapacities(seriesMatches);
        return seriesMatches;
    }

    private Map<Long, LocalDateTime> normalizeOrderStartTimes(Map<Long, LocalDateTime> orderStartTimes) {
        if (orderStartTimes == null || orderStartTimes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LocalDateTime> normalized = new HashMap<Long, LocalDateTime>();
        for (Map.Entry<Long, LocalDateTime> entry : orderStartTimes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey(), normalizePlanStart(entry.getValue()));
        }
        return normalized;
    }

    private LocalDateTime normalizePlanStart(LocalDateTime startAt) {
        if (startAt == null) {
            return null;
        }
        return startAt.withNano(0);
    }

    private LocalDateTime resolveDemandStartAt(Long orderId,
                                               Map<Long, LocalDateTime> orderStartTimes,
                                               LocalDateTime defaultStartAt) {
        if (orderId != null && orderStartTimes != null) {
            LocalDateTime custom = orderStartTimes.get(orderId);
            if (custom != null) {
                return custom;
            }
        }
        return normalizePlanStart(defaultStartAt);
    }

    private int parseOrderPriority(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            return 0;
        }
        String normalized = priority.trim();
        if ("插单".equals(normalized)) {
            return 2;
        }
        if ("加急".equals(normalized)) {
            return 1;
        }
        if ("普通".equals(normalized)) {
            return 0;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    private int remainingOrderQuantity(ProductionOrder order) {
        int qty = Optional.ofNullable(order.getQuantity()).orElse(0);
        int assigned = Optional.ofNullable(order.getAssignedQuantity()).orElse(0);
        return Math.max(0, qty - assigned);
    }

    private void schedulePairedBarDemands(LocalDate day,
                                          LocalDate endExclusive,
                                          List<RingPairDemand> pairDemands,
                                          Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                          List<PlanSlice> plannedSlices,
                                          Map<String, LineActivationPlan> activationPlanByKey,
                                          String objective) {
        for (RingPairDemand pairDemand : pairDemands) {
            if (pairDemand.remaining() <= 0 || !pairDemand.canScheduleOn(day)) {
                continue;
            }
            List<LineCapacity> prioritizedLines = prioritizeCandidateLines(pairDemand.activationKey(), pairDemand.sharedBarLines(), day,
                    endExclusive, pairDemand.remaining(), remainingCapacityByLineDay, activationPlanByKey, objective);
            assignDemandToLines(day, pairDemand, prioritizedLines, remainingCapacityByLineDay,
                    (line, assignQty) -> {
                        plannedSlices.add(new PlanSlice(day, line.lineId, line.lineName, pairDemand.customer(), "LA", pairDemand.laModel(), assignQty, line.capacityPerHour));
                        plannedSlices.add(new PlanSlice(day, line.lineId, line.lineName, pairDemand.customer(), "LB", pairDemand.lbModel(), assignQty, line.capacityPerHour));
                    });
        }
    }

    private void assignDemandToLines(LocalDate day,
                                     PlannableDemand demand,
                                     List<LineCapacity> prioritizedLines,
                                     Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                     SliceAppender sliceAppender) {
        for (LineCapacity line : prioritizedLines) {
            if (demand.remaining() <= 0) {
                break;
            }
            LineDayKey lineDayKey = new LineDayKey(line.lineId, day);
            int remainingCapacity = remainingCapacityByLineDay.getOrDefault(lineDayKey, 0);
            if (remainingCapacity <= 0) {
                continue;
            }
            int assignQty = Math.min(remainingCapacity, demand.remaining());
            demand.applyPlan(assignQty, day);
            remainingCapacityByLineDay.put(lineDayKey, remainingCapacity - assignQty);
            sliceAppender.append(line, assignQty);
        }
    }

    private interface SliceAppender {
        void append(LineCapacity line, int assignQty);
    }

    private static class OrderDemandSeed {
        private final ProductionOrder order;
        private final int requiredQty;

        private OrderDemandSeed(ProductionOrder order, int requiredQty) {
            this.order = order;
            this.requiredQty = requiredQty;
        }
    }

    private static class PairCandidate {
        private final DemandItem lbDemand;
        private final String sharedSeries;
        private final List<LineCapacity> sharedBarLines;

        private PairCandidate(DemandItem lbDemand, String sharedSeries, List<LineCapacity> sharedBarLines) {
            this.lbDemand = lbDemand;
            this.sharedSeries = sharedSeries;
            this.sharedBarLines = sharedBarLines;
        }
    }

    private class LineActivationPlan {
        private final Set<Long> activatedLineIds = new HashSet<Long>();

        private void ensureMinimumLines(List<LineCapacity> sortedCandidates,
                                        LocalDate day,
                                        LocalDate endExclusive,
                                        int demandRemaining,
                                        Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                        String objective) {
            if (sortedCandidates == null || sortedCandidates.isEmpty() || demandRemaining <= 0) {
                return;
            }
            int targetCapacity = targetCapacity(sortedCandidates, day, endExclusive, demandRemaining, remainingCapacityByLineDay, objective);
            if (targetCapacity <= 0) {
                return;
            }
            int coveredCapacity = activatedDayCapacity(sortedCandidates, day, remainingCapacityByLineDay);
            if (coveredCapacity >= targetCapacity) {
                return;
            }
            for (LineCapacity candidate : sortedCandidates) {
                if (activatedLineIds.contains(candidate.lineId)) {
                    continue;
                }
                activatedLineIds.add(candidate.lineId);
                coveredCapacity += dayRemainingCapacity(candidate, day, remainingCapacityByLineDay);
                if (coveredCapacity >= targetCapacity) {
                    break;
                }
            }
        }

        private int activatedDayCapacity(List<LineCapacity> sortedCandidates,
                                         LocalDate day,
                                         Map<LineDayKey, Integer> remainingCapacityByLineDay) {
            int total = 0;
            for (LineCapacity line : sortedCandidates) {
                if (!activatedLineIds.contains(line.lineId)) {
                    continue;
                }
                total += dayRemainingCapacity(line, day, remainingCapacityByLineDay);
            }
            return total;
        }

        private List<LineCapacity> activatedLines(List<LineCapacity> sortedCandidates,
                                                  LocalDate day,
                                                  LocalDate endExclusive,
                                                  int demandRemaining,
                                                  Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                                  String objective) {
            ensureMinimumLines(sortedCandidates, day, endExclusive, demandRemaining, remainingCapacityByLineDay, objective);
            List<LineCapacity> activatedToday = sortedCandidates.stream()
                    .filter(line -> activatedLineIds.contains(line.lineId))
                    .filter(line -> remainingCapacityByLineDay.getOrDefault(new LineDayKey(line.lineId, day), 0) > 0)
                    .collect(Collectors.toList());
            if (activatedToday.isEmpty() || demandRemaining <= 0) {
                return activatedToday;
            }
            int targetCapacity = targetCapacity(sortedCandidates, day, endExclusive, demandRemaining, remainingCapacityByLineDay, objective);
            List<LineCapacity> minimalLines = new ArrayList<LineCapacity>();
            int covered = 0;
            for (LineCapacity line : activatedToday) {
                minimalLines.add(line);
                covered += dayRemainingCapacity(line, day, remainingCapacityByLineDay);
                if (covered >= targetCapacity) {
                    break;
                }
            }
            activatedLineIds.clear();
            minimalLines.stream().map(line -> line.lineId).forEach(activatedLineIds::add);
            return minimalLines;
        }

        private int targetCapacity(List<LineCapacity> sortedCandidates,
                                   LocalDate day,
                                   LocalDate endExclusive,
                                   int demandRemaining,
                                   Map<LineDayKey, Integer> remainingCapacityByLineDay,
                                   String objective) {
            if (!OBJECTIVE_MIN_LINE.equals(objective)) {
                return Math.min(demandRemaining, totalDayCapacity(sortedCandidates, day, remainingCapacityByLineDay));
            }
            long daysLeftLong = Math.max(1L, ChronoUnit.DAYS.between(day, endExclusive));
            int daysLeft = (int) Math.min(Integer.MAX_VALUE, daysLeftLong);
            int dailyTarget = BigDecimal.valueOf(demandRemaining)
                    .divide(BigDecimal.valueOf(daysLeft), 0, RoundingMode.CEILING)
                    .multiply(DAILY_TARGET_BUFFER)
                    .setScale(0, RoundingMode.CEILING)
                    .intValue();
            return Math.min(demandRemaining, dailyTarget);
        }

        private int totalDayCapacity(List<LineCapacity> lines,
                                     LocalDate day,
                                     Map<LineDayKey, Integer> remainingCapacityByLineDay) {
            int total = 0;
            for (LineCapacity line : lines) {
                total += dayRemainingCapacity(line, day, remainingCapacityByLineDay);
            }
            return total;
        }
    }

    private LocalDateTime resolveActualPlanStart(List<PlanSlice> slices, LocalDateTime effectiveStartAt, LocalDate fallbackStart) {
        if (slices == null || slices.isEmpty()) {
            return fallbackStart == null ? null : fallbackStart.atStartOfDay();
        }
        LocalDate earliestSliceDay = slices.stream()
                .map(PlanSlice::day)
                .min(LocalDate::compareTo)
                .orElse(fallbackStart);
        if (earliestSliceDay == null) {
            return null;
        }
        if (effectiveStartAt != null && earliestSliceDay.equals(effectiveStartAt.toLocalDate())) {
            return effectiveStartAt;
        }
        return earliestSliceDay.atStartOfDay();
    }

    private LocalDateTime resolveActualPlanEnd(List<PlanSlice> slices, LocalDateTime actualStart) {
        if (slices == null || slices.isEmpty()) {
            return actualStart;
        }
        LocalDate latestSliceDay = slices.stream()
                .map(PlanSlice::day)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestSliceDay == null) {
            return actualStart;
        }
        return latestSliceDay.plusDays(1).atStartOfDay();
    }

    private boolean allDemandsCompleted(List<DemandItem> demands) {
        return demands.stream().allMatch(demand -> demand.remaining() <= 0);
    }

    private LineCapacity pickHigherCapacityLine(LineCapacity left, LineCapacity right) {
        int compare = left.capacityPerHour.compareTo(right.capacityPerHour);
        if (compare > 0) {
            return left;
        }
        if (compare < 0) {
            return right;
        }
        return compareLinePriority(left, right) <= 0 ? left : right;
    }

    private int compareByRemainingHorizonCapacity(LineCapacity left,
                                                  LineCapacity right,
                                                  LocalDate startDay,
                                                  LocalDate endExclusive,
                                                  Map<LineDayKey, Integer> remainingCapacityByLineDay) {
        int dailyCapacityCompare = Integer.compare(
                dayRemainingCapacity(right, startDay, remainingCapacityByLineDay),
                dayRemainingCapacity(left, startDay, remainingCapacityByLineDay)
        );
        if (dailyCapacityCompare != 0) {
            return dailyCapacityCompare;
        }
        int capacityCompare = Integer.compare(
                totalRemainingCapacity(right, startDay, endExclusive, remainingCapacityByLineDay),
                totalRemainingCapacity(left, startDay, endExclusive, remainingCapacityByLineDay)
        );
        if (capacityCompare != 0) {
            return capacityCompare;
        }
        return compareLinePriority(left, right);
    }

    private int dayRemainingCapacity(LineCapacity line,
                                     LocalDate day,
                                     Map<LineDayKey, Integer> remainingCapacityByLineDay) {
        return remainingCapacityByLineDay.getOrDefault(new LineDayKey(line.lineId, day), 0);
    }

    private int totalRemainingCapacity(LineCapacity line,
                                       LocalDate startDay,
                                       LocalDate endExclusive,
                                       Map<LineDayKey, Integer> remainingCapacityByLineDay) {
        int total = 0;
        LocalDate cursor = startDay;
        while (cursor.isBefore(endExclusive)) {
            total += remainingCapacityByLineDay.getOrDefault(new LineDayKey(line.lineId, cursor), 0);
            cursor = cursor.plusDays(1);
        }
        return total;
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

    private void sortLineCapacities(List<LineCapacity> lineCapacities) {
        lineCapacities.sort(this::compareLinePriority);
    }

    private int compareLinePriority(LineCapacity left, LineCapacity right) {
        return Comparator
                .comparing((LineCapacity l) -> l.priority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(line -> line.lineId)
                .compare(left, right);
    }

    private int comparePairCandidate(PairCandidate left, PairCandidate right) {
        int sharedSeriesCompare = Integer.compare(right.sharedSeries.length(), left.sharedSeries.length());
        if (sharedSeriesCompare != 0) {
            return sharedSeriesCompare;
        }
        int sharedLineCountCompare = Integer.compare(right.sharedBarLines.size(), left.sharedBarLines.size());
        if (sharedLineCountCompare != 0) {
            return sharedLineCountCompare;
        }
        int deliveryCompare = Integer.compare(left.lbDemand.deliveryUrgencyDays(), right.lbDemand.deliveryUrgencyDays());
        if (deliveryCompare != 0) {
            return deliveryCompare;
        }
        int requiredCompare = Integer.compare(right.lbDemand.required(), left.lbDemand.required());
        if (requiredCompare != 0) {
            return requiredCompare;
        }
        return compareLinePriority(left.sharedBarLines.get(0), right.sharedBarLines.get(0));
    }

    private String findBestSharedSeries(DemandLineMatch left, DemandLineMatch right) {
        if (left == null || right == null) {
            return null;
        }
        return left.seriesKeys().stream()
                .filter(right.seriesKeys()::contains)
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .orElse(null);
    }

    private List<LineCapacity> findSharedBarLines(DemandLineMatch left, DemandLineMatch right, String sharedSeries) {
        if (left == null || right == null || sharedSeries == null) {
            return Collections.emptyList();
        }
        List<LineCapacity> leftLines = left.barLinesBySeries(sharedSeries);
        List<LineCapacity> rightLines = right.barLinesBySeries(sharedSeries);
        if (leftLines.isEmpty() || rightLines.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, LineCapacity> pairableByLineId = new HashMap<Long, LineCapacity>();
        for (LineCapacity line : leftLines) {
            pairableByLineId.merge(line.lineId, line, this::pickHigherCapacityLine);
        }
        for (LineCapacity line : rightLines) {
            pairableByLineId.merge(line.lineId, line, this::pickHigherCapacityLine);
        }
        List<LineCapacity> pairableLines = new ArrayList<LineCapacity>(pairableByLineId.values());
        sortLineCapacities(pairableLines);
        return pairableLines;
    }
}
