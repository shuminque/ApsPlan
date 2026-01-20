package com.depository_manage.entity.aps;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.util.Date;

@Data
public class ShiftSchedule {

    private Long scheduleId;

    /** 排班日期（逻辑日期） */
    private Date scheduleDate;

    /** 班组ID */
    private String teamID;

    /** 班次代码 */
    private String shiftCode;

    /** 实际开始时间（datetime） */
    private Date startDateTime;

    /** 实际结束时间（datetime） */
    private Date endDateTime;

    /** 备注 */
    private String remark;

    private Date createTime;
    private Date updateTime;



    @TableField(exist = false)
    private String startTime;   // "08:00"

    @TableField(exist = false)
    private String endTime;     // "17:00"
}
