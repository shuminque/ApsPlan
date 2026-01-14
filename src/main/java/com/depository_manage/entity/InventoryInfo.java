package com.depository_manage.entity;

import lombok.Data;

@Data
public class InventoryInfo {
    private String outerInnerRing;
    private String model;
    private int quantity;
    private int isStocked;
    private int totalBoxes; // 新增字段，用于存储总箱数
    private int quantityInStock;
    private String boxText;
    private String boxNumber;

    // 构造方法、getters 和 setters 省略
}
