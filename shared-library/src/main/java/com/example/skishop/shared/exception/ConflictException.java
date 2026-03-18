package com.example.skishop.shared.exception;

/**
 * リソースの競合が発生した場合にスローされる例外 (HTTP 409)。
 */
public class ConflictException extends RuntimeException {

    private final String errorCode;

    public ConflictException(String message) {
        super(message);
        this.errorCode = "CONFLICT";
    }

    public ConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
