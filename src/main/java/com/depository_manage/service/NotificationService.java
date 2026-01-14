package com.depository_manage.service;

import com.depository_manage.entity.Notification;

import java.util.List;
import java.util.Map;

public interface NotificationService {
    /**
     * 插入一条通知记录
     * @param notification 通知信息
     * @return 受影响的行数
     */
    Integer insertNotification(Notification notification);

    /**
     * 根据id删除一条通知记录
     * @param id 通知id
     * @return 受影响的行数
     */
    Integer deleteNotificationById(Integer id);

    /**
     * 根据id修改通知记录
     * @param notification 通知信息
     * @return 受影响的行数
     */
    Integer updateNotification(Notification notification);

    /**
     * 根据id主键查询通知
     *
     * @param id 通知id
     * @return 该id的通知记录
     */
    Notification findNotificationById(Integer id);

    /**
     * 查找所有通知记录
     * @return 所有的通知记录集合
     */
    List<Notification> findAllNotifications();

    /**
     * 根据条件查询通知记录，同时支持分页查询（需要begin和size参数）
     * @param map 查询参数
     * @return 符合条件的通知记录集合
     */
    List<Notification> findNotificationsByCondition(Map<String, Object> map);

    /**
     * 根据用户ID查询未读的通知记录
     *
     * @param userId 用户ID
     * @return 未读的通知记录集合
     */
    List<Notification> findUnreadNotificationsByUserId(Integer userId);

    /**
     * 根据id标记通知为已读
     * @param id 通知id
     * @return 受影响的行数
     */
    Integer markNotificationAsRead(Integer id);

}
