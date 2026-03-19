package com.example.skishop.mailsend.service;

public class EmailSendException extends RuntimeException {

    private final Integer statusCode;
    private final Long retryAfterMs;

    public EmailSendException(String message, Throwable cause, Integer statusCode, Long retryAfterMs) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterMs = retryAfterMs;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public Long retryAfterMs() {
        return retryAfterMs;
    }

    public boolean isRetryable() {
        if (statusCode == null) {
            return true;
        }
        if (statusCode == 429) {
            return true;
        }
        return statusCode >= 500 && statusCode <= 599;
    }
}
