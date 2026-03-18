package com.example.skishop.authentication.exception;

public class TokenExpiredException extends AuthenticationException {
    public TokenExpiredException() {
        super("トークンの有効期限が切れています");
    }
}
