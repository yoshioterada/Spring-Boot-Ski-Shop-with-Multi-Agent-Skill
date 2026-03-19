package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record ModelVersionResponse(
        String id,
        String modelTrainingId,
        String version,
        boolean isActive,
        Map<String, Object> performance,
        Instant deployedAt
) {}
