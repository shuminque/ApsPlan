package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("production_line")
public class ProductionLine {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("line_code")
    private String lineCode;

    @TableField("line_name")
    private String lineName;

    @TableField("workshop")
    private String workshop;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
