package com.depository_manage.pojo;

import com.depository_manage.entity.ProductInfo;
import lombok.Data;
import java.util.Date;

@Data
public class ProductInfoP {
    private Integer id;
    private Date date;
    private Integer smallDiameter;
    private Integer mediumDiameter;
    private Integer gimbal;
    private Integer rab;
    private Integer depositoryId;

    public ProductInfoP(Integer id, Date date, Integer smallDiameter, Integer mediumDiameter, Integer gimbal, Integer rab, Integer depositoryId){
        this.id = id;
        this.date = date;
        this.smallDiameter = smallDiameter;
        this.mediumDiameter = mediumDiameter;
        this.gimbal = gimbal;
        this.rab = rab;
        this.depositoryId = depositoryId;
    }

    public ProductInfoP(ProductInfo productInfo){
        this.id = productInfo.getId();
        this.date = productInfo.getDate();
        this.smallDiameter = productInfo.getSmallDiameter();
        this.mediumDiameter = productInfo.getMediumDiameter();
        this.gimbal = productInfo.getGimbal();
        this.rab = productInfo.getRab();
        this.depositoryId = productInfo.getDepositoryId();
    }
}
