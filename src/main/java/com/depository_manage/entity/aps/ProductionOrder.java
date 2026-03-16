package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("production_order")
public class ProductionOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String customer;
    private String outerInnerRing;
    private String model;
    private Integer quantity;

    private Date orderDate;
    private Date deliveryDate;

    private String status;
    private String priority;
    private Integer finishedQuantity;
    private String remark;
    /**
     * 已分配排产数量，避免重复排产。
     */
    @TableField("assigned_quantity")
    private Integer assignedQuantity;

    private Date createdAt;
    private Date updatedAt;
}
