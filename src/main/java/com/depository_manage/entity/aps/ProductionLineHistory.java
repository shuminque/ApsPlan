package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("production_line_history")
public class ProductionLineHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("line_id")
    private Long lineId;

    @TableField(exist = false)
    private String lineCode;

    @TableField(exist = false)
    private String lineName;

    @TableField("model")
    private String model;

    @TableField("event_type")
    private Integer eventType;

    @TableField("start_time")
    private Date startTime;

    @TableField("end_time")
    private Date endTime;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("remark")
    private String remark;

    @TableField("create_time")
    private Date createTime;
}
