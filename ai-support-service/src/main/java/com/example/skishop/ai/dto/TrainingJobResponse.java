package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record TrainingJobResponse(
        String trainingId,
        String modelType,
        String algorithm,
        String status,
        String version,
        Map<String, Object> metrics,
        Instant startTime,
        Instant endTime
) {}
