package com.depository_manage.pojo.shift;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PlanPreviewResponseDTO {
    private List<CalendarEventDTO> events = new ArrayList<>();
    private List<PlanPreviewOrderDTO> orders = new ArrayList<>();
    private List<PlanPreviewDailyDTO> dailyOutputs = new ArrayList<>();
}
