package com.depository_manage.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Notification {
    private Integer id;
    private Integer userId;
    private String content;
    private Date dateCreated;
    private Boolean isRead;
}
