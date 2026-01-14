package com.depository_manage.entity.shift;

import lombok.Data;

@Data
public class ShiftDefinition {
    private Integer shiftId;
    private String shiftCode;
    private String shiftName;
    private Double durationHours;
    private String startTime;  // HH:mm:ss
    private String endTime;    // HH:mm:ss
    private Boolean isOverNight;
    private Integer displayOrder;
}
