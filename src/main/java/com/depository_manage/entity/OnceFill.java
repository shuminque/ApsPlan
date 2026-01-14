package com.depository_manage.entity;

import lombok.Data;

import java.util.Date;

@Data
public class OnceFill {
    private Integer id;
    private Integer depositoryId;
    private String name;
    private String model;
    /** 材料种类id */
    private Integer typeId;
    private Double unitPrice;
    private Integer quantity;
    private Double price;
    private Date time;
    private String applyRemark;
    private String introduce;

}
