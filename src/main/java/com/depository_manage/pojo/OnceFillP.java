package com.depository_manage.pojo;

import com.depository_manage.entity.OnceFill;
import lombok.Data;

import java.util.Date;
@Data
public class OnceFillP {
    private Integer id;
    private Integer depositoryId;
    private String name;
    private String model;
    /** 材料种类名称 */
    private Integer typeId;
    private String typeName;
    private Double unitPrice;
    private Integer quantity;
    private Double price;
    private Date time;
    private String applyRemark;
    private String introduce;

    public OnceFillP(Integer id,  String name, String model, Integer quantity, Double price, Double unitPrice,Integer typeId, String typeName,
                     Integer depositoryId, String applyRemark, Date time, String introduce) {
        this.id = id;
        this.name = name;
        this.model =model;
        this.quantity = quantity;
        this.price = price;
        this.unitPrice=unitPrice;
        this.typeId = typeId;
        this.typeName = typeName;
        this.depositoryId = depositoryId;
        this.applyRemark = applyRemark;
        this.time =time;
        this.introduce = introduce;
    }
    public OnceFillP(OnceFill onceFill) {
        this.id = onceFill.getId();
        this.name = onceFill.getName();
        this.typeId = onceFill.getTypeId();
        this.model =onceFill.getModel();
        this.quantity = onceFill.getQuantity();
        this.price = onceFill.getPrice();
        this.unitPrice=onceFill.getUnitPrice();
        this.depositoryId = onceFill.getDepositoryId();
        this.applyRemark = onceFill.getApplyRemark();
        this.time =onceFill.getTime();
        this.introduce = onceFill.getIntroduce();
    }
}
