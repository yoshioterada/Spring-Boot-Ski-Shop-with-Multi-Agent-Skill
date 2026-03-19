package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record ModelPerformanceResponse(
        String modelType,
        String version,
        Map<String, Object> metrics,
        Instant analyzedAt
) {}
