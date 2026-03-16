package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("production_plan")
public class ProductionPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planBatchNo;
    private String source;
    private Date startDate;
    private Date endDate;
    private Integer totalAssignQty;
    @TableField("create_time")
    private Date createdAt;
    @TableField("update_time")
    private Date updatedAt;
}

