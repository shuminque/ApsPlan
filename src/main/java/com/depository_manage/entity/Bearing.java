package com.depository_manage.entity;

import lombok.Data;

@Data
public class Bearing {
    private Integer id;
    private String boxText;
    private String boxNumber;
    private String model;
    private String customer;
    private Integer quantity;
    private String outerInnerRing;   // 外/内轮
    private String productCategory;  // 制品分类
    private String steelType;        // 钢种
    private String steelGrade;       // 钢材等级
    private String depository;       // 厂区
    private String storageLocation;  // 库位
    private String size;  // 尺寸
    private Integer pair;
    private String state;
    private String currentDepository;
    private Double singleEight;
    private String  mode;
    // Lombok 的 @Data 注解会自动生成构造函数、getter 和 setter 方法
}
