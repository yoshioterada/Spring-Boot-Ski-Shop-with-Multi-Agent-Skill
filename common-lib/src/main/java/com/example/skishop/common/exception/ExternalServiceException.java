package com.example.skishop.common.exception;

public final class ExternalServiceException extends ApplicationException {

    private final String serviceName;

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super("EXTERNAL_SERVICE_ERROR", message, cause);
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }
}
