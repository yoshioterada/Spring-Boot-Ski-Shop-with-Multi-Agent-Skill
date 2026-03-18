package com.example.skishop.shared.exception;

/**
 * リソースが見つからない場合にスローされる例外 (HTTP 404)。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super("%s が見つかりません: %s".formatted(resourceType, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
