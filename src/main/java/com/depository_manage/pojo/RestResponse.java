package com.depository_manage.pojo;


import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 所有服务统一响应数据格式
 */
@Accessors(chain = true)
@Data
public class RestResponse implements Serializable {
    private int code; // 对应 Layui 的code
    private String msg; // 对应 Layui 的msg
    /**
     * 业务数据
     */
    private Object data;
    /**
     * 数据条数
     */
    private int count;
    /**
     * 状态码
     */
    private int status=200;
    private boolean success; // 新增字段
    private String message;  // 新增字段
    /**
     * 状态信息
     */
    private StatusInfo statusInfo=new StatusInfo();

    public RestResponse() {
    }

    public RestResponse(Object data) {
        this.data = data;
    }

    public RestResponse(Object data, int count, int status) {
        this.data = data;
        this.count = count;
        this.status = status;
    }

    public RestResponse(Object data, int status, StatusInfo statusInfo) {
        this.data = data;
        this.status = status;
        this.statusInfo = statusInfo;
    }
    public RestResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    public RestResponse(int code, String msg, int count, Object data) {
        this.code = code;
        this.msg = msg;
        this.count = count;
        this.data = data;
    }

}

