package com.depository_manage.exceptionHandler;

import com.depository_manage.exception.MyException;
import com.depository_manage.exception.OperationAlreadyDoneException; // 确保导入 OperationAlreadyDoneException
import com.depository_manage.pojo.RestResponse;
import com.depository_manage.pojo.StatusInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
@Slf4j
public class CustomExceptionHandler {

    @ResponseBody
    @ExceptionHandler(value = MyException.class)
    public RestResponse handleMyException(MyException e) {
        // MyException 的处理逻辑...
        return new RestResponse(null, e.getCode(), new StatusInfo(e.getMsg(), e.getMsg()));
    }

    @ExceptionHandler(value = OperationAlreadyDoneException.class)
    @ResponseBody
    public RestResponse handleOperationAlreadyDoneException(OperationAlreadyDoneException e) {
        // 处理 OperationAlreadyDoneException...
        int errorCode = HttpStatus.CONFLICT.value(); // 或其他适当的错误代码
        return new RestResponse(null, errorCode, new StatusInfo(e.getMessage(), e.getMessage()));
    }
    // 可能还有其他异常处理方法...
}
