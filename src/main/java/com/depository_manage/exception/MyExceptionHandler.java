package com.depository_manage.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class MyExceptionHandler {

    @ExceptionHandler(MyException.class)
    @ResponseBody
    public ResponseEntity<Object> handleMyException(MyException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        // 使用 MyException 中的 code 作为 HTTP 响应的状态码
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        if (status == null) status = HttpStatus.BAD_REQUEST; // 如果 code 不是有效的HTTP状态码，则默认为 400

        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMsg());

        return new ResponseEntity<>(body, status);
    }
}
