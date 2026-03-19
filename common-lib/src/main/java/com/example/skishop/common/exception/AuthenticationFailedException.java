package com.example.skishop.common.exception;

public final class AuthenticationFailedException extends ApplicationException {

    public AuthenticationFailedException(String message) {
        super("AUTHENTICATION_FAILED", message);
    }
}
