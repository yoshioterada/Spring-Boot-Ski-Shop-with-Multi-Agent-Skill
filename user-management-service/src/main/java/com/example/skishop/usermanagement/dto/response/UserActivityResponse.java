package com.example.skishop.usermanagement.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserActivityResponse(
    Long id,
    UUID userId,
    String activityType,
    OffsetDateTime timestamp,
    String details,
    String ipAddress,
    String deviceInfo
) {}
