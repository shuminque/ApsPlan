package com.depository_manage.pojo;

import lombok.Data;

@Data
public class SimpleUser {
    /** 用户id */
    private Integer id;

    /** 用户名称 */
    private String uname;

    /** 密码 */
    private String pwd;

    /** 头像地址 */
    private String pictureUrl;
}
