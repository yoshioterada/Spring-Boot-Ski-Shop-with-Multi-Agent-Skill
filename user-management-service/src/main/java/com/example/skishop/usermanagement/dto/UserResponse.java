package com.example.skishop.usermanagement.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String address,
        LocalDate birthDate,
        String gender,
        String status,
        boolean emailVerified,
        boolean phoneVerified,
        String roleName,
        Instant createdAt,
        Instant updatedAt
) {}
