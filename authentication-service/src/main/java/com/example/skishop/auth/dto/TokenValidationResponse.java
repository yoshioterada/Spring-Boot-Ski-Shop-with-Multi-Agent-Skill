package com.example.skishop.auth.dto;

import java.time.Instant;

public record TokenValidationResponse(
        boolean valid,
        String userId,
        String email,
        String role,
        Instant expiresAt
) {}
