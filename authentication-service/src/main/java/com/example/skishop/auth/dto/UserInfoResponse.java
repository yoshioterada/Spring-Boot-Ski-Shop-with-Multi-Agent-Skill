package com.example.skishop.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserInfoResponse(
        UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        String role,
        boolean emailVerified,
        Instant lastLogin
) {}
