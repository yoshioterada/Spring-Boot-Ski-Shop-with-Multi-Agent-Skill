package com.example.skishop.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        String accessToken,
        String refreshToken,
        Instant expiresAt
) {}
