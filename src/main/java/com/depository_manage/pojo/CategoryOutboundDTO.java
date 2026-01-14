package com.depository_manage.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryOutboundDTO {
    private String month;
    private String category;
    private BigDecimal amount;

}
