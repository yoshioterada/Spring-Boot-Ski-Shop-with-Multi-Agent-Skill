package com.example.skishop.authentication.exception;

public class InvalidTokenException extends AuthenticationException {
    public InvalidTokenException() {
        super("無効なトークンです");
    }
    public InvalidTokenException(String message) {
        super(message);
    }
}
