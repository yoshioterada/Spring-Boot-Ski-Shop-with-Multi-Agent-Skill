package com.example.skishop.common.exception;

public final class BusinessRuleViolationException extends ApplicationException {

    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
