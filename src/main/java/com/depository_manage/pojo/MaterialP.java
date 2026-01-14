package com.depository_manage.pojo;

import com.depository_manage.entity.Material;
import lombok.Data;

@Data
public class MaterialP {
    /** 存储id */
    private Integer id;

    /**AT号*/
    private Integer atId;

    /** 仓库名称 */
    private String depositoryName;

    /** 材料名称 */
    private String mname;

    /**型号/规格*/
    private String model;

    /** 数量 */
    private Double quantity;

    /** 总金额 */
    private Double price;
    private Double unitPrice;

    /** 材料种类名称 */
    private Integer typeId;
    private String typeName;

    private String picNum;
    private Integer stateId;
    private String stateName;
    private Integer enginId;
    private String enginName;

    public MaterialP(Integer id, Integer atId, Integer depositoryId, String mname, String model, Double quantity, Double price, Double unitPrice, Integer typeId, String typeName,
                     String picNum, String stateName, Integer enginId, String enginName) {
        this.id = id;
        this.atId=atId;
        this.typeId=typeId;
        this.mname = mname;
        this.model =model;
        this.quantity = quantity;
        this.price = price;
        this.unitPrice=unitPrice;
        this.typeName = typeName;
        this.picNum = picNum;
        this.stateName =stateName;
        this.enginId=enginId;
        this.enginName =enginName;
    }
    public MaterialP(Material material) {
        this.id = material.getId();
        this.atId=material.getAtId();
        this.typeId= material.getTypeId();
        this.mname = material.getMname();
        this.model = material.getModel();
        this.quantity = material.getQuantity();
        this.picNum = material.getPicNum();
        this.price = material.getPrice();
        this.enginId = material.getEnginId();
        this.unitPrice=material.getUnitPrice();
        this.stateId = material.getStateId();
    }
}
