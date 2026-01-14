package com.depository_manage.exception;

public class OperationAlreadyDoneException extends RuntimeException {
    public OperationAlreadyDoneException(String message) {
        super(message);
    }
}
