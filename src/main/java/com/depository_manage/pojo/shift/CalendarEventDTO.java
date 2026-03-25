package com.depository_manage.pojo.shift;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class CalendarEventDTO {
    private Long id;         // 对应 scheduleId
    private String title;    // 显示班次名称+班组
    private String start;    // yyyy-MM-ddTHH:mm
    private String end;      // yyyy-MM-ddTHH:mm
    private String shiftStartTime;
    private String shiftEndTime;
    private String color;    // 颜色
    private String eventType; // SHIFT / PLAN
    private String source;    // 来源标识
    /**
     * 以下字段用于自动排产预览/提交之间传递结构化信息，
     * 避免 commit 阶段只能依赖标题文本反解析，导致匹配失败。
     */
    private Long lineId;
    private String lineName;
    private String customer;
    private String outerInnerRing;
    private String model;
    private Integer quantity;
    /**
     * 结果指标（结构化输出，前端直接渲染）
     */
    private BigDecimal avgDailyWorkHours;
    private BigDecimal dailyOutput;
    private BigDecimal capacityPerHour;
    /**
     * 指标诊断标记：
     * - OK：指标完整可计算
     * - MISSING_CAPACITY_CONFIG：缺少产能配置，相关指标无法计算
     * - INVALID_CAPACITY_CONFIG：产能配置<=0，相关指标无法计算
     */
    private String metricDiagnosticTag;
}
