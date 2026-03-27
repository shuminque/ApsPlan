package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.utils.CraftMappingUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionPlanningCoreAlgorithmTest {

    private final ProductionPlanningServiceImpl service = new ProductionPlanningServiceImpl();

    @Test
    void shouldBuildDemandWithInsertLockByPureMemory() {
        ProductionOrder autoOrder = order(1L, "C1", "LA", "6205", 100, 0, LocalDate.now().plusDays(3), CraftMappingUtil.PIPE_CRAFT);
        ProductionOrder insertOrder = order(2L, "C1", "LA", "6205", 50, 0, LocalDate.now().plusDays(1), CraftMappingUtil.PIPE_CRAFT);

        Map<String, List<ProductionOrder>> orderByKey = new HashMap<String, List<ProductionOrder>>();
        orderByKey.put("K", Arrays.asList(autoOrder, insertOrder));

        @SuppressWarnings("unchecked")
        List<Object> demands = (List<Object>) ReflectionTestUtils.invokeMethod(service, "buildDemands",
                orderByKey,
                Collections.emptyMap(),
                Collections.emptyMap(),
                "INSERT",
                Collections.singleton(2L),
                Collections.emptyMap(),
                LocalDateTime.now());

        assertEquals(2, demands.size());
        Object first = demands.get(0);
        Boolean lockedInsert = (Boolean) ReflectionTestUtils.getField(first, "lockedInsert");
        assertTrue(lockedInsert);
    }

    @Test
    void shouldPairLaLbDemandsBySharedSeries() {
        Object la = newDemand(11L, "C1", "LA", "LA6205", 60, CraftMappingUtil.BAR_CRAFT);
        Object lb = newDemand(12L, "C1", "LB", "LB6205", 60, CraftMappingUtil.BAR_CRAFT);
        List<Object> demands = Arrays.asList(la, lb);

        Map<String, List<LineCapacity>> caps = new HashMap<String, List<LineCapacity>>();
        caps.put("6205", Collections.singletonList(LineCapacity.of(3L, "L3", "6205", new BigDecimal("10"), 1, CraftMappingUtil.BAR_CRAFT)));

        @SuppressWarnings("unchecked")
        Map<Object, Object> matches = (Map<Object, Object>) ReflectionTestUtils.invokeMethod(service, "buildBarLineMatchesByDemand", demands, caps);
        @SuppressWarnings("unchecked")
        List<Object> pairs = (List<Object>) ReflectionTestUtils.invokeMethod(service, "buildRingPairDemands", demands, matches);

        assertEquals(1, pairs.size());
    }

    @Test
    void shouldActivateMinimalLinesForMinLineObjective() throws Exception {
        List<LineCapacity> lines = Arrays.asList(
                LineCapacity.of(1L, "L1", "6205", new BigDecimal("10"), 1, CraftMappingUtil.PIPE_CRAFT),
                LineCapacity.of(2L, "L2", "6205", new BigDecimal("10"), 2, CraftMappingUtil.PIPE_CRAFT)
        );

        Class<?> lineDayKeyClass = Class.forName("com.depository_manage.service.aps.impl.LineDayKey");
        java.lang.reflect.Constructor<?> ctor = lineDayKeyClass.getDeclaredConstructor(Long.class, LocalDate.class);
        ctor.setAccessible(true);
        LocalDate day = LocalDate.now();
        Map<Object, Integer> remaining = new HashMap<Object, Integer>();
        remaining.put(ctor.newInstance(1L, day), 120);
        remaining.put(ctor.newInstance(2L, day), 120);

        @SuppressWarnings("unchecked")
        List<LineCapacity> picked = (List<LineCapacity>) ReflectionTestUtils.invokeMethod(service,
                "prioritizeCandidateLines",
                "AUTO|C1|LA|6205",
                lines,
                day,
                day.plusDays(2),
                80,
                remaining,
                new HashMap<String, Object>(),
                "min_line");

        assertEquals(1, picked.size());
        assertEquals(Long.valueOf(1L), picked.get(0).getLineId());
    }

    private Object newDemand(Long id, String customer, String ring, String model, int required, String craft) {
        try {
            java.lang.reflect.Constructor<?> ctor = ReflectionUtilsHolder.demandItemClass().getDeclaredConstructor(
                    Long.class, String.class, String.class, String.class, String.class,
                    int.class, int.class, int.class, int.class, int.class, Date.class,
                    int.class, boolean.class, LocalDateTime.class, LocalDate.class, ZoneId.class);
            ctor.setAccessible(true);
            return ctor.newInstance(
                    id, customer, ring, model, craft, required, 0, 1, required, 0,
                    Date.from(LocalDate.now().plusDays(3).atStartOfDay(ZoneId.systemDefault()).toInstant()),
                    0, false, LocalDateTime.now(), LocalDate.now(), ZoneId.systemDefault());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ProductionOrder order(Long id, String customer, String ring, String model, int qty, int assigned, LocalDate due, String craft) {
        ProductionOrder order = new ProductionOrder();
        order.setId(id);
        order.setCustomer(customer);
        order.setOuterInnerRing(ring);
        order.setModel(model);
        order.setCraft(craft);
        order.setQuantity(qty);
        order.setAssignedQuantity(assigned);
        order.setStatus("0");
        order.setPriority("普通");
        order.setDeliveryDate(Date.from(due.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return order;
    }

    private static final class ReflectionUtilsHolder {
        static Class<?> demandItemClass() {
            try {
                return Class.forName("com.depository_manage.service.aps.impl.ProductionPlanningServiceImpl$DemandItem");
            } catch (ClassNotFoundException e) {
                try {
                    return Class.forName("com.depository_manage.service.aps.impl.DemandItem");
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
    }
}
