package com.depository_manage.pojo.shift;

import lombok.Data;

@Data
public class PlanPreviewOrderDTO {
    private String customer;
    private String outerInnerRing;
    private String model;
    private String earliestDeliveryDate;
    private Integer currentInventory;
    private Integer requiredQuantity;
    private Integer plannedQuantity;
    private Integer plannedDays;
}
