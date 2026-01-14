package com.depository_manage.pojo;

import com.depository_manage.entity.SimpleDepositoryRecord;
import com.depository_manage.utils.DateUtil;
import lombok.Data;

@Data
public class SimpleDepositoryRecordP {
    /** 记录id */
    private Integer id;

    /** 调度记录类型（购入/退料/转入,退还/领料/转出) */
    private Integer type;

    /** 申请人id */
    private String applicantName;

    /** 申请备注 */
    private String applyRemark;

    private Integer review_group_id;

    /** 申请时间 */
    private String applyTime;
    /**AT号*/
    private Integer atId;
    /** 产品名称 */
    private String mname;
    /**型号/规格*/
    private String model;
    private Double price;
    /** 数量 */
    private Double quantity;

    public SimpleDepositoryRecordP(SimpleDepositoryRecord d) {
        this.id=d.getId();
        this.type = d.getType();
        this.applyRemark = d.getApplyRemark();
        this.review_group_id = d.getReview_group_id();
        this.applyTime = DateUtil.getSimpleTime(d.getApplyTime());
        this.atId = d.getAtId();
        this.mname = d.getMname();
        this.model = d.getModel();
        this.price = d.getPrice();
        this.quantity = d.getQuantity();
    }
}
