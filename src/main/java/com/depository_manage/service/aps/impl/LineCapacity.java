package com.depository_manage.service.aps.impl;

import com.depository_manage.utils.CraftMappingUtil;

import java.math.BigDecimal;

import static com.depository_manage.service.aps.impl.ProductionPlanningServiceImpl.normalizeCraft;

public class LineCapacity {
    final Long lineId;
    final String lineName;
    final String model;
    final BigDecimal capacityPerHour;
    final Integer priority;
    final String craft;

    private LineCapacity(Long lineId, String lineName, String model, BigDecimal capacityPerHour, Integer priority, String craft) {
        this.lineId = lineId;
        this.lineName = lineName;
        this.model = model;
        this.capacityPerHour = capacityPerHour;
        this.priority = priority;
        this.craft = normalizeCraft(craft);
    }

    public static LineCapacity of(Long lineId, String lineName, String model, BigDecimal capacityPerHour, Integer priority, String craft) {
        return new LineCapacity(lineId, lineName, model, capacityPerHour, priority, craft);
    }

    public Long getLineId() {
        return lineId;
    }

    public boolean isBarCraft() {
        return CraftMappingUtil.BAR_CRAFT.equals(craft);
    }

    public boolean matchesCraft(String requiredCraft) {
        if (requiredCraft == null) {
            return true;
        }
        return requiredCraft.equals(craft);
    }
}
