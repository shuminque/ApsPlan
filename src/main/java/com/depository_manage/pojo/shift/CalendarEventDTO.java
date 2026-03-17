package com.depository_manage.pojo.shift;


import lombok.Data;

@Data
public class CalendarEventDTO {
    private Long id;         // 对应 scheduleId
    private String title;    // 显示班次名称+班组
    private String start;    // yyyy-MM-ddTHH:mm
    private String end;      // yyyy-MM-ddTHH:mm
    private String shiftStartTime;
    private String shiftEndTime;
    private String color;    // 颜色
}
