package com.depository_manage.pojo.shift;

import lombok.Data;

@Data
public class PlanPreviewDailyDTO {
    private String day;
    private String lineName;
    private String customer;
    private String outerInnerRing;
    private String model;
    private Integer quantity;
}
