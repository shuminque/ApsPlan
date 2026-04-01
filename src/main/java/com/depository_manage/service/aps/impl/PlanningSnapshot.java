package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.entity.aps.ProductionLineRuntime;
import com.depository_manage.entity.aps.SafetyStock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PlanningSnapshot {

    private final List<ProductionOrder> openOrders;
    private final List<SafetyStock> safetyStocks;
    private final Map<String, List<ProductionOrder>> orderByKey;
    private final Map<String, SafetyStock> safetyStockByKey;
    private final Map<String, Integer> currentInventoryByKey;
    private final Map<LocalDate, BigDecimal> shiftHoursByDay;
    private final List<ProductionLineModelConfig> lineModelConfigs;
    private final List<ProductionLine> productionLines;
    private final Map<String, List<LineCapacity>> lineCapByModel;
    private final Map<LineDayKey, Integer> remainingCapacityByLineDay;
    private final Map<Long, LineRuntimeView> runtimeViewByLineId;

    public PlanningSnapshot(List<ProductionOrder> openOrders,
                            List<SafetyStock> safetyStocks,
                            Map<String, List<ProductionOrder>> orderByKey,
                            Map<String, SafetyStock> safetyStockByKey,
                            Map<String, Integer> currentInventoryByKey,
                            Map<LocalDate, BigDecimal> shiftHoursByDay,
                            List<ProductionLineModelConfig> lineModelConfigs,
                            List<ProductionLine> productionLines,
                            Map<String, List<LineCapacity>> lineCapByModel,
                            Map<LineDayKey, Integer> remainingCapacityByLineDay,
                            Map<Long, LineRuntimeView> runtimeViewByLineId) {
        this.openOrders = openOrders;
        this.safetyStocks = safetyStocks;
        this.orderByKey = orderByKey;
        this.safetyStockByKey = safetyStockByKey;
        this.currentInventoryByKey = currentInventoryByKey;
        this.shiftHoursByDay = shiftHoursByDay;
        this.lineModelConfigs = lineModelConfigs;
        this.productionLines = productionLines;
        this.lineCapByModel = lineCapByModel;
        this.remainingCapacityByLineDay = remainingCapacityByLineDay;
        this.runtimeViewByLineId = runtimeViewByLineId;
    }

    public List<ProductionOrder> getOpenOrders() {
        return openOrders;
    }

    public List<SafetyStock> getSafetyStocks() {
        return safetyStocks;
    }

    public Map<String, List<ProductionOrder>> getOrderByKey() {
        return orderByKey;
    }

    public Map<String, SafetyStock> getSafetyStockByKey() {
        return safetyStockByKey;
    }

    public Map<String, Integer> getCurrentInventoryByKey() {
        return currentInventoryByKey;
    }

    public Map<LocalDate, BigDecimal> getShiftHoursByDay() {
        return shiftHoursByDay;
    }

    public List<ProductionLineModelConfig> getLineModelConfigs() {
        return lineModelConfigs;
    }

    public List<ProductionLine> getProductionLines() {
        return productionLines;
    }

    public Map<String, List<LineCapacity>> getLineCapByModel() {
        return lineCapByModel;
    }

    public Map<LineDayKey, Integer> getRemainingCapacityByLineDay() {
        return remainingCapacityByLineDay;
    }

    public Map<Long, LineRuntimeView> getRuntimeViewByLineId() {
        return runtimeViewByLineId;
    }

    public static class LineRuntimeView {
        private final Integer status;
        private final String currentModel;
        private final BigDecimal currentCapacity;
        private final LocalDateTime changeoverStartTime;
        private final LocalDateTime changeoverEndTime;

        public LineRuntimeView(Integer status,
                               String currentModel,
                               BigDecimal currentCapacity,
                               LocalDateTime changeoverStartTime,
                               LocalDateTime changeoverEndTime) {
            this.status = status;
            this.currentModel = currentModel;
            this.currentCapacity = currentCapacity;
            this.changeoverStartTime = changeoverStartTime;
            this.changeoverEndTime = changeoverEndTime;
        }

        public static LineRuntimeView fromRuntime(ProductionLineRuntime runtime, LocalDateTime changeoverStartTime, LocalDateTime changeoverEndTime) {
            if (runtime == null) {
                return null;
            }
            return new LineRuntimeView(runtime.getStatus(), runtime.getCurrentModel(), runtime.getCurrentCapacity(),
                    changeoverStartTime, changeoverEndTime);
        }

        public Integer getStatus() {
            return status;
        }

        public String getCurrentModel() {
            return currentModel;
        }

        public BigDecimal getCurrentCapacity() {
            return currentCapacity;
        }

        public LocalDateTime getChangeoverStartTime() {
            return changeoverStartTime;
        }

        public LocalDateTime getChangeoverEndTime() {
            return changeoverEndTime;
        }
    }
}
