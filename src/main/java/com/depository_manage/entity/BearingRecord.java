package com.depository_manage.entity;

import lombok.Data;
import java.util.Date;

@Data
public class BearingRecord {
    private int id;
    private String transactionType;
    private String boxText;
    private String boxNumber;
    private int quantity;
    private Date time; // 使用 java.util.Date 来表示 datetime 类型
    private String operator;
    private String depository;
    private String storageLocation;
    private String customer;
    private String outerInnerRing;
    private String model;
    private String productCategory;
    private String steelType;
    private String steelGrade;
    private String remarks;
    private Date lastRecordedTime;
    private int iter;
    private String size;             // 新增字段：尺寸
    private String dissolve;         // 新增字段：溶解号
    private int pair;
    private String state;
    private String currentDepository;
    private Double singleEight;
    private String  mode;
    // Lombok @Data 注解会自动生成构造函数、getter 和 setter 方法、toString 方法等
}
