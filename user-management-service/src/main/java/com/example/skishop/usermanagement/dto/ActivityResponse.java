package com.example.skishop.usermanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        UUID userId,
        String activityType,
        String description,
        Instant createdAt
) {}
