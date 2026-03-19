package com.example.skishop.common.exception;

/**
 * Base exception hierarchy using sealed classes for type-safe error handling.
 */
public sealed class ApplicationException extends RuntimeException
        permits ResourceNotFoundException, BusinessRuleViolationException,
                ExternalServiceException, AuthenticationFailedException,
                AuthorizationDeniedException {

    private final String errorCode;

    public ApplicationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApplicationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
