package com.depository_manage.service.impl;

import com.depository_manage.entity.Notification;
import com.depository_manage.mapper.cpck.old.NotificationMapper;
import com.depository_manage.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Override
    public Integer insertNotification(Notification notification) {
        return notificationMapper.insertNotification(notification);
    }

    @Override
    public Integer deleteNotificationById(Integer id) {
        return notificationMapper.deleteNotificationById(id);
    }

    @Override
    public Integer updateNotification(Notification notification) {
        return notificationMapper.updateNotification(notification);
    }

    @Override
    public Notification findNotificationById(Integer id) {
        return notificationMapper.findNotificationById(id);
    }

    @Override
    public List<Notification> findAllNotifications() {
        return notificationMapper.findAllNotifications();
    }

    @Override
    public List<Notification> findNotificationsByCondition(Map<String, Object> map) {
        return notificationMapper.findNotificationsByCondition(map);
    }


    @Override
    public List<Notification> findUnreadNotificationsByUserId(Integer userId) {
        return notificationMapper.findUnreadNotificationsByUserId(userId);
    }

    @Override
    public Integer markNotificationAsRead(Integer id) {
        return notificationMapper.markNotificationAsRead(id);
    }

}
