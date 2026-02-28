package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("production_line_model_config")
public class ProductionLineModelConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("line_id")
    private Long lineId;

    @TableField(exist = false)
    private String lineName;

    @TableField("model")
    private String model;

    @TableField("capacity_per_hour")
    private BigDecimal capacityPerHour;

    @TableField("small_changeover_time")
    private Integer smallChangeoverTime;

    @TableField("large_changeover_time")
    private Integer largeChangeoverTime;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
