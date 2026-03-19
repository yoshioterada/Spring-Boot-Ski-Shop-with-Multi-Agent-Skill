package com.example.skishop.usermanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record PreferenceResponse(
        UUID id,
        String key,
        String value,
        String type,
        Instant updatedAt
) {}
