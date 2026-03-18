package com.example.skishop.authentication.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TokenValidationResponse(
    boolean valid,
    String subject,
    List<String> scopes,
    LocalDateTime expiresAt
) {
    public static TokenValidationResponse invalid() {
        return new TokenValidationResponse(false, null, List.of(), null);
    }
}
