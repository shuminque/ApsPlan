package com.depository_manage.service.aps;

import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.pojo.shift.PlanPreviewResponseDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 规则优先级排产服务。
 */
public interface ProductionPlanningService {

    /**
     * 基于当前订单、安全库存、班次与产线配置计算排产事件，并用于日历展示。
     */
    List<CalendarEventDTO> generatePlanCalendarEvents(String startDate, String endDate);

    PlanPreviewResponseDTO generatePlanPreview(String startDate, String endDate);

    PlanPreviewResponseDTO generatePlanPreview(String startDate,
                                               String endDate,
                                               String planMode,
                                               List<Long> insertOrderIds,
                                               String lineScope,
                                               List<Long> lineIds,
                                               Integer freezeHours,
                                               Map<Long, LocalDateTime> orderStartTimes);
}
