package com.example.skishop.usermanagement.dto.response;

import com.example.skishop.usermanagement.model.UserStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phoneNumber,
    LocalDate birthDate,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime lastLoginAt,
    UserStatus status,
    List<String> roles
) {}
