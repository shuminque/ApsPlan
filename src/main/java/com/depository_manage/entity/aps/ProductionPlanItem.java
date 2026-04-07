package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("production_plan_item")
public class ProductionPlanItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private String planBatchNo;
    private Long orderId;
    private String customer;
    private String model;
    private String outerInnerRing;
    private Long lineId;
    private String lineName;
    private Date startDate;
    private Date endDate;
    private Integer assignQty;
    @TableField("order_demand_qty")
    private Integer orderDemandQty;
    @TableField("safety_demand_qty")
    private Integer safetyDemandQty;
    private String source;
    @TableField("create_time")
    private Date createdAt;
    @TableField("update_time")
    private Date updatedAt;
}
