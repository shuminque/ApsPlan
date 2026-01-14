package com.depository_manage.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ProductInfo {
    private Integer id;
    private Date date;
    private Integer smallDiameter;
    private Integer mediumDiameter;
    private Integer gimbal;
    private Integer rab;
    private Integer depositoryId;
}
