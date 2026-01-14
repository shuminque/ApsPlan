package com.depository_manage.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class ProductId {
    private String boxText;
    private String boxNumber;
    private Integer quantity; // 具体装箱数
    private Integer depositoryId; // 厂区ID
    private Integer isStocked; // 表示是否已入库（0未入库，1已入库）
    private int iter; // 轮数
    private Date creationTime;

    // Lombok @Data 注解会自动生成构造函数、getter、setter、toString等
}
