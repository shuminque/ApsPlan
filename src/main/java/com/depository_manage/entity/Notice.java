package com.depository_manage.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Notice implements java.io.Serializable {
    /** 版本号 */
    private static final long serialVersionUID = -2744388334561767209L;

    /** 公告主键 */
    private Integer id;

    /** 公告标题 */
    private String title;

    /** 公告内容 */
    private String content;

    /** 发布时间 */
    private Date time;

    /** AT号 */
    private Integer atId;

    /** 品名 */
    private String mname;

    /** 厂区ID */
    private Integer depositoryId;

    /** 型号 */
    private String model;

    /** 分类名称 */
    private String typeName;

    private Double lastCount;


    // Since we are using Lombok @Data, there's no need to manually add getters and setters.


}
