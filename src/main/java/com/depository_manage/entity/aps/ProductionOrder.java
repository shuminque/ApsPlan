package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date; // 切换到传统的 Date

@Data
@TableName("production_order") // 确保和数据库改名后的表名一致
public class ProductionOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String customer;
    private String outerInnerRing;
    private String model;
    private Integer quantity;

    // 使用 java.util.Date
    private Date orderDate;
    private Date deliveryDate;

    private String status;
    private String priority;
    private Integer finishedQuantity;
    private String remark;

    // 使用 java.util.Date
    private Date createdAt;
    private Date updatedAt;
}