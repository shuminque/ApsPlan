package com.depository_manage.service.aps.impl;

import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.service.aps.ProductionPlanningService;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.service.aps.ShiftCalendarService;
import com.depository_manage.service.aps.planning.NormalizedPlanningRequest;
import com.depository_manage.service.aps.planning.PlanningRequest;
import com.depository_manage.service.aps.planning.PlanningRequestNormalizer;
import com.depository_manage.utils.CraftMappingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProductionPlanningServiceImpl implements ProductionPlanningService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String PLAN_COLOR = "#FFB020";
    private static final String OBJECTIVE_MIN_LINE = "min_line";
    private static final Logger log = LoggerFactory.getLogger(ProductionPlanningServiceImpl.class);
    private Clock clock = Clock.systemDefaultZone();
    private ZoneId zoneId = ZoneId.systemDefault();
    private final PlanningRequestNormalizer planningRequestNormalizer = new PlanningRequestNormalizer();
    private final PlanningResultMapper planningResultMapper = new PlanningResultMapper(DATE_TIME_FMT, PLAN_COLOR);
    private final PlanningEngine v1PlanningEngine = new PlanningEngineV1();
    private final PlanningEngine v2PlanningEngine = new PlanningEngineV2();

    @Value("${aps.planning.engine:v1}")
    private String planningEngineMode = "v1";

    @Resource
    private ProductionOrderService productionOrderService;
    @Resource
    private SafetyStockService safetyStockService;
    @Resource
    private ShiftCalendarService shiftCalendarService;
    @Resource
    private ProductionLineModelConfigMapper modelConfigMapper;
    @Resource
    private ProductionLineMapper productionLineMapper;
    @Resource
    private BearingRecordService bearingRecordService;

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
    public List<CalendarEventDTO> generatePlanCalendarEvents(String startDate, String endDate) {
        return generatePlanPreview(startDate, endDate).getEvents();
    }

    @Override
    public PlanPreviewResponseDTO generatePlanPreview(String startDate, String endDate) {
        return generatePlanPreview(new PlanningRequest(startDate, endDate, "AUTO", Collections.emptyList(), "ALL",
                Collections.emptyList(), null, Collections.emptyMap(), OBJECTIVE_MIN_LINE));
    }

    @Override
    public PlanPreviewResponseDTO generatePlanPreview(PlanningRequest request) {
        NormalizedPlanningRequest normalizedRequest = planningRequestNormalizer.normalize(request, clock);
        return generatePlanPreview(normalizedRequest);
    }

    @Override
    public PlanPreviewResponseDTO generatePlanPreview(String startDate,
                                                      String endDate,
                                                      String planMode,
                                                      List<Long> insertOrderIds,
                                                      String lineScope,
                                                      List<Long> lineIds,
                                                      Integer freezeHours,
                                                      Map<Long, LocalDateTime> orderStartTimes,
                                                      String objective) {
        return generatePlanPreview(PlanningRequest.fromLegacyParameters(startDate, endDate, planMode, insertOrderIds,
                lineScope, lineIds, freezeHours, orderStartTimes, objective));
    }

    private PlanPreviewResponseDTO generatePlanPreview(NormalizedPlanningRequest normalizedRequest) {
        PlanningContext context = createPlanningContext(normalizedRequest);
        return generatePlanPreviewWithContext(context);
    }

    private PlanningContext createPlanningContext(NormalizedPlanningRequest normalizedRequest) {
        PlanningInputAssembler inputAssembler = new PlanningInputAssembler(
                productionOrderService,
                safetyStockService,
                shiftCalendarService,
                modelConfigMapper,
                productionLineMapper,
                bearingRecordService,
                zoneId
        );
        PlanningSnapshot snapshot = inputAssembler.assemble(normalizedRequest);
        return new PlanningContext(normalizedRequest, snapshot, clock, zoneId);
    }

    private PlanPreviewResponseDTO generatePlanPreviewWithContext(PlanningContext context) {
        NormalizedPlanningRequest normalizedRequest = context.getNormalizedRequest();
        LocalDate requestStart = normalizedRequest.getRequestStart();
        LocalDate endExclusive = normalizedRequest.getEndExclusive();
        if (requestStart == null || endExclusive == null) {
            return new PlanPreviewResponseDTO();
        }

        PlanningResult primaryResult = runPrimaryEngine(context);
        if ("shadow".equalsIgnoreCase(planningEngineMode)) {
            PlanningResult shadowResult = runEngine(v2PlanningEngine, context);
            logShadowDiff(normalizedRequest, primaryResult, shadowResult);
        }

        PlanningResult.Metrics metrics = planningResultMapper.calculateMetrics(primaryResult.getDemands(), endExclusive);
        PlanningResult resultWithMetrics = new PlanningResult(primaryResult.getSlices(), primaryResult.getDemands(),
                primaryResult.getActualStart(), primaryResult.getActualEnd(), metrics, primaryResult.getDiagnostics());
        return planningResultMapper.toPlanPreviewResponse(resultWithMetrics,
                normalizedRequest.getEffectiveStartAt(), context.getSnapshot().getShiftHoursByDay());
    }

    private PlanningResult runPrimaryEngine(PlanningContext context) {
        String mode = planningEngineMode == null ? "v1" : planningEngineMode.trim().toLowerCase(Locale.ROOT);
        if ("v2".equals(mode)) {
            return runEngine(v2PlanningEngine, context);
        }
        return runEngine(v1PlanningEngine, context);
    }

    private PlanningResult runEngine(PlanningEngine engine, PlanningContext context) {
        PlanningResult result = engine.plan(context);
        if (result == null) {
            return new PlanningResult(Collections.emptyList(), Collections.emptyList(), null, null,
                    new PlanningResult.Metrics(0, 0, BigDecimal.ONE), null);
        }
        return result;
    }

    private void logShadowDiff(NormalizedPlanningRequest request, PlanningResult v1Result, PlanningResult v2Result) {
        String v1Slices = summarizeSlices(v1Result);
        String v2Slices = summarizeSlices(v2Result);
        String v1Metrics = summarizeMetrics(v1Result);
        String v2Metrics = summarizeMetrics(v2Result);
        String v1Dto = summarizeDto(v1Result, request);
        String v2Dto = summarizeDto(v2Result, request);
        if (!Objects.equals(v1Slices, v2Slices) || !Objects.equals(v1Metrics, v2Metrics) || !Objects.equals(v1Dto, v2Dto)) {
            log.warn("[planning-shadow] diff detected start={}, endExclusive={}, slices(v1)={}, slices(v2)={}, metrics(v1)={}, metrics(v2)={}, dto(v1)={}, dto(v2)={}",
                    request.getStart(), request.getEndExclusive(), v1Slices, v2Slices, v1Metrics, v2Metrics, v1Dto, v2Dto);
        }
    }

    private String summarizeSlices(PlanningResult result) {
        return result.getSlices().stream()
                .sorted(Comparator.comparing(PlanSlice::day)
                        .thenComparing(PlanSlice::lineId)
                        .thenComparing(PlanSlice::customer, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(PlanSlice::outerInnerRing, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(PlanSlice::model, Comparator.nullsFirst(String::compareTo)))
                .map(slice -> String.join("|",
                        String.valueOf(slice.day()),
                        String.valueOf(slice.lineId()),
                        String.valueOf(slice.customer()),
                        String.valueOf(slice.outerInnerRing()),
                        String.valueOf(slice.model()),
                        String.valueOf(slice.quantity())))
                .collect(Collectors.joining(","));
    }

    private String summarizeMetrics(PlanningResult result) {
        PlanningResult.Metrics metrics = planningResultMapper.calculateMetrics(result.getDemands(),
                result.getActualEnd() == null ? LocalDate.now(clock).plusDays(1) : result.getActualEnd().toLocalDate());
        return metrics.getSqueezedOrderCount() + "|" + metrics.getDelayedDays() + "|" + metrics.getInsertFulfillmentRate();
    }

    private String summarizeDto(PlanningResult result, NormalizedPlanningRequest request) {
        PlanningResult.Metrics metrics = planningResultMapper.calculateMetrics(result.getDemands(), request.getEndExclusive());
        PlanningResult resultWithMetrics = new PlanningResult(result.getSlices(), result.getDemands(), result.getActualStart(),
                result.getActualEnd(), metrics, result.getDiagnostics());
        PlanPreviewResponseDTO dto = planningResultMapper.toPlanPreviewResponse(resultWithMetrics,
                request.getEffectiveStartAt(), Collections.emptyMap());
        return dto.getEvents().size() + "|" + dto.getOrders().size() + "|" + dto.getDailyOutputs().size() + "|"
                + dto.getPlanStart() + "|" + dto.getPlanEnd() + "|" + dto.getSqueezedOrderCount() + "|"
                + dto.getDelayedDays() + "|" + dto.getInsertFulfillmentRate();
    }

    static String normalizeCraft(String craft) {
        String normalized = CraftMappingUtil.normalizeCraft(craft);
        if (normalized != null) {
            return normalized;
        }
        if (craft == null || craft.trim().isEmpty()) {
            return null;
        }
        String value = craft.trim();
        if (value.contains("棒")) {
            return CraftMappingUtil.BAR_CRAFT;
        }
        if (value.contains("管")) {
            return CraftMappingUtil.PIPE_CRAFT;
        }
        if (value.contains("锻")) {
            return CraftMappingUtil.FORGING_CRAFT;
        }
        return null;
    }


}
