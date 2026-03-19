package com.example.skishop.common.exception;

public final class AuthorizationDeniedException extends ApplicationException {

    public AuthorizationDeniedException(String message) {
        super("AUTHORIZATION_DENIED", message);
    }
}
