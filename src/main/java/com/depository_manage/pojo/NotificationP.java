package com.depository_manage.pojo;

import com.depository_manage.entity.Notification;
import lombok.Data;

import java.util.Date;

@Data
public class NotificationP {
    private Integer id;
    private Integer userId;
    private String content;
    private Date dateCreated;
    private Boolean isRead;

    public NotificationP(Integer id, Integer userId, String content, Date dateCreated, Boolean isRead) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.dateCreated = dateCreated;
        this.isRead = isRead;
    }

    public NotificationP(Notification notification) {
        this.id = notification.getId();
        this.userId = notification.getUserId();
        this.content = notification.getContent();
        this.dateCreated = notification.getDateCreated();
        this.isRead = notification.getIsRead();
    }
}
