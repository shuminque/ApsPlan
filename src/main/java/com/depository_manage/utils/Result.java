package com.depository_manage.utils; // 换成你项目的实际包名

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;    // 状态码 200成功, 500失败
    private String msg;      // 提示信息
    private T data;          // 返回的数据

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMsg(msg);
        return result;
    }
}