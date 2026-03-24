package com.depository_manage.pojo.shift;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlanPreviewResponseDTO {
    private List<CalendarEventDTO> events = new ArrayList<>();
    private List<PlanPreviewOrderDTO> orders = new ArrayList<>();
    private List<PlanPreviewDailyDTO> dailyOutputs = new ArrayList<>();
    private Integer squeezedOrderCount = 0;
    private Integer delayedDays = 0;
    private BigDecimal insertFulfillmentRate = BigDecimal.ZERO;
}
