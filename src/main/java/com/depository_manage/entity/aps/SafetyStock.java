package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("safety_stock")
public class SafetyStock {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String customer;
    private String outerInnerRing;
    private String model;
    private BigDecimal stockCycle; // 单位：月
    private Boolean isCustom;

    private Date createdAt;
    private Date updatedAt;
}
