package com.depository_manage.entity;

import lombok.Data;

import java.util.Date;

@Data
public class SimpleDepositoryRecord {
    /** 记录id */
    private Integer id;

    /** 调度记录类型（购入/退料/转入,退还/领料/转出) */
    private Integer type;

    /** 申请人id */
    private Integer applicantId;

    /** 申请备注 */
    private String applyRemark;

    private Integer review_group_id;

    /** 申请时间 */
    private Date applyTime;

    /**AT号*/
    private Integer atId;
    /** 产品名称 */
    private String mname;
    /**型号/规格*/
    private String model;

    /** 数量 */
    private Double quantity;
    private Double price;
}
