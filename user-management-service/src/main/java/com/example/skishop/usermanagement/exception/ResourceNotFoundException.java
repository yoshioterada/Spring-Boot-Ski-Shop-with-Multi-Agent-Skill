package com.example.skishop.usermanagement.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " が見つかりません: " + id);
    }
}
