package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("production_line_runtime")
public class ProductionLineRuntime {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("line_id")
    private Long lineId;

    @TableField(exist = false)
    private String lineName;

    @TableField(exist = false)
    private String lineCode;

    @TableField("current_model")
    private String currentModel;

    @TableField("current_capacity")
    private BigDecimal currentCapacity;

    @TableField("status")
    private Integer status;

    @TableField("changeover_start_time")
    private Date changeoverStartTime;

    @TableField("changeover_end_time")
    private Date changeoverEndTime;

    @TableField("update_time")
    private Date updateTime;
}
