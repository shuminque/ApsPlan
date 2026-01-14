package com.depository_manage.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
@Data
public class RecordDTO {

    private String sourceTable;      // 来源表（once_fill 或 depository_record）
    private Integer id;              // 记录的 ID
    private Integer atId;
    private String name;             // 产品名称
    private String model;            // 产品型号
    private Double quantity;         // 数量
    private BigDecimal unitPrice;    // 单价
    private BigDecimal totalAmount;  // 总金额
    private Date recordDate;         // 记录日期
    private String applyRemark;      // 申请备注
    private String categoryTitle;    // 类别名称
}
