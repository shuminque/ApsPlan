package com.depository_manage.entity;

import lombok.Data;

@Data
public class BearingInventory {
    private int id; // 新增字段，用于存储唯一标识符
    private String boxText;
    private String boxNumber;
    private int quantityInStock;
    private int totalBoxes; // 新增字段，用于存储总箱数
    private int depositoryId; // 保持为 int 类型
    private int iter;
    private String operationType; // 新增字段，用于区分操作类型

    // Lombok @Data 注解将自动生成构造方法、getter 和 setter
}
