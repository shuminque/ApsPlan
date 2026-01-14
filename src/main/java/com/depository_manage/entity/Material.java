package com.depository_manage.entity;

import lombok.Data;

/**
 * 产品信息记录（库存）(material)
 */
@Data
public class Material {
    /** 版本号 */
    private static final long serialVersionUID = 4604245526757565755L;

    /** 存储id */
    private Integer id;

    /**AT号*/
    private Integer atId;

    /** 仓库名称 */
    private Integer depositoryId;

    /** 材料名称 */
    private String mname;

    /**型号/规格*/
    private String model;

    /** 数量 */
    private Double quantity;

    /** 总金额 */
    private Double price;

    private Double unitPrice;

    /** 材料种类id */
    private Integer typeId;

    /**图号*/
    private String picNum;

    /**材料状态id*/
    private Integer stateId;

    /**工程名称id*/
    private Integer enginId;

}