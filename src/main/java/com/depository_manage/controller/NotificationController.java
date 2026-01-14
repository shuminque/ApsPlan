package com.depository_manage.controller;

import com.depository_manage.entity.Notification;
import com.depository_manage.pojo.RestResponse;
import com.depository_manage.security.bean.UserToken;
import com.depository_manage.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping
    public Integer insertNotification(@RequestBody Notification notification) {
        return notificationService.insertNotification(notification);
    }

    @DeleteMapping("/{id}")
    public Integer deleteNotificationById(@PathVariable Integer id) {
        return notificationService.deleteNotificationById(id);
    }

    @PutMapping
    public Integer updateNotification(@RequestBody Notification notification) {
        return notificationService.updateNotification(notification);
    }

    @GetMapping("/{id}")
    public Notification findNotificationById(@PathVariable Integer id) {
        return notificationService.findNotificationById(id);
    }

    @GetMapping("/all")
    public List<Notification> findAllNotifications() {
        return notificationService.findAllNotifications();
    }

    @GetMapping
    public List<Notification> findNotificationsByCondition(@RequestParam Map<String, Object> map) {
        return notificationService.findNotificationsByCondition(map);
    }

    @GetMapping("/unread")
    public RestResponse getNotificationsForCurrentUser(HttpServletRequest request) {
        UserToken userToken = (UserToken) request.getAttribute("userToken");
        Integer userId = userToken.getUser().getId();
        List<Notification> notifications = notificationService.findUnreadNotificationsByUserId(userId);
        return new RestResponse(notifications, notifications.size(), 200);
    }

    @PutMapping("/mark-as-read/{id}")
    public Integer markNotificationAsRead(@PathVariable Integer id) {
        return notificationService.markNotificationAsRead(id);
    }
}
