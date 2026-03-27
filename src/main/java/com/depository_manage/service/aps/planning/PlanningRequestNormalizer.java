package com.depository_manage.service.aps.planning;

import com.depository_manage.exception.MyException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PlanningRequestNormalizer {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String OBJECTIVE_MIN_LINE = "min_line";
    private static final String OBJECTIVE_LEGACY_CAPACITY = "legacy_capacity";

    public NormalizedPlanningRequest normalize(PlanningRequest request, Clock clock) {
        LocalDateTime requestStartAt = toLocalDateTime(request.getStartDate());
        LocalDate requestStart = requestStartAt == null ? null : requestStartAt.toLocalDate();
        LocalDate endExclusive = toLocalDate(request.getEndDate());
        if (requestStart == null || endExclusive == null) {
            return new NormalizedPlanningRequest(requestStart, requestStart, endExclusive,
                    requestStartAt, requestStartAt, normalizeMode(request.getPlanMode()),
                    normalizeObjective(request.getObjective()), Collections.emptySet(),
                    Collections.emptySet(), 0, Collections.emptyMap());
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate start = requestStart.isBefore(today) ? today : requestStart;
        LocalDateTime effectiveStartAt = requestStartAt.withSecond(0).withNano(0);
        if (effectiveStartAt.toLocalDate().isBefore(start)) {
            effectiveStartAt = start.atStartOfDay();
        }
        if (!start.isBefore(endExclusive)) {
            endExclusive = start.plusMonths(1);
        }

        int freezeWindowHours = request.getFreezeHours() == null ? 0 : Math.max(0, request.getFreezeHours());
        if (freezeWindowHours > 0) {
            LocalDateTime freezeEnd = requestStartAt.plusHours(freezeWindowHours);
            if (freezeEnd.isAfter(effectiveStartAt)) {
                effectiveStartAt = freezeEnd;
                start = effectiveStartAt.toLocalDate();
                if (!start.isBefore(endExclusive)) {
                    endExclusive = start.plusMonths(1);
                }
            }
        }

        return new NormalizedPlanningRequest(
                requestStart,
                start,
                endExclusive,
                requestStartAt,
                effectiveStartAt,
                normalizeMode(request.getPlanMode()),
                normalizeObjective(request.getObjective()),
                normalizeLongSet(request.getInsertOrderIds()),
                normalizeLineScope(request.getLineScope(), request.getLineIds()),
                freezeWindowHours,
                parseOrderStartTimes(request.getOrderStartTimes())
        );
    }

    private Map<Long, LocalDateTime> parseOrderStartTimes(Map<String, String> orderStartTimes) {
        if (orderStartTimes == null || orderStartTimes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LocalDateTime> parsed = new HashMap<>();
        for (Map.Entry<String, String> entry : orderStartTimes.entrySet()) {
            String orderIdRaw = entry.getKey();
            String startRaw = entry.getValue();
            if (orderIdRaw == null || orderIdRaw.trim().isEmpty()) {
                throw new MyException(400, "orderStartTimes 包含空的订单ID");
            }
            if (startRaw == null || startRaw.trim().isEmpty()) {
                throw new MyException(400, "订单 " + orderIdRaw + " 的开始时间不能为空");
            }
            Long orderId;
            try {
                orderId = Long.parseLong(orderIdRaw.trim());
            } catch (NumberFormatException ex) {
                throw new MyException(400, "orderStartTimes 的订单ID必须是数字，错误值: " + orderIdRaw);
            }
            parsed.put(orderId, parseDateTime(startRaw.trim(), orderIdRaw));
        }
        return parsed;
    }

    private LocalDateTime parseDateTime(String value, String orderId) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter minuteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        DateTimeFormatter secondFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value, dateFormatter).atStartOfDay();
            }
            if (value.length() == 16) {
                return LocalDateTime.parse(value, minuteFormatter);
            }
            if (value.length() == 19) {
                return LocalDateTime.parse(value, secondFormatter);
            }
        } catch (DateTimeParseException ex) {
            throw new MyException(400, "订单 " + orderId + " 的开始时间格式错误: " + value
                    + "，仅支持 yyyy-MM-dd / yyyy-MM-ddTHH:mm / yyyy-MM-ddTHH:mm:ss");
        }
        throw new MyException(400, "订单 " + orderId + " 的开始时间格式错误: " + value
                + "，仅支持 yyyy-MM-dd / yyyy-MM-ddTHH:mm / yyyy-MM-ddTHH:mm:ss");
    }

    private LocalDateTime toLocalDateTime(String dateTime) {
        if (dateTime == null || dateTime.trim().isEmpty()) {
            return null;
        }
        String normalized = dateTime.trim();
        if (normalized.length() == 10) {
            return LocalDate.parse(normalized).atStartOfDay();
        }
        if (normalized.length() == 16) {
            return LocalDateTime.parse(normalized + ":00", DATE_TIME_FMT);
        }
        if (normalized.length() >= 19) {
            return LocalDateTime.parse(normalized.substring(0, 19), DATE_TIME_FMT);
        }
        return LocalDate.parse(normalized.substring(0, 10)).atStartOfDay();
    }

    private LocalDate toLocalDate(String dateTime) {
        if (dateTime == null || dateTime.length() < 10) {
            return null;
        }
        return LocalDate.parse(dateTime.substring(0, 10));
    }

    private String normalizeMode(String planMode) {
        if (planMode == null || planMode.trim().isEmpty()) {
            return "AUTO";
        }
        String normalized = planMode.trim().toUpperCase();
        return "INSERT".equals(normalized) ? "INSERT" : "AUTO";
    }

    private String normalizeObjective(String objective) {
        if (objective == null || objective.trim().isEmpty()) {
            return OBJECTIVE_MIN_LINE;
        }
        String normalized = objective.trim().toLowerCase(Locale.ROOT);
        if (OBJECTIVE_LEGACY_CAPACITY.equals(normalized)) {
            return OBJECTIVE_LEGACY_CAPACITY;
        }
        return OBJECTIVE_MIN_LINE;
    }

    private Set<Long> normalizeLongSet(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Set<Long> normalizeLineScope(String lineScope, List<Long> lineIds) {
        if (!"PARTIAL".equalsIgnoreCase(lineScope)) {
            return Collections.emptySet();
        }
        return normalizeLongSet(lineIds);
    }
}
