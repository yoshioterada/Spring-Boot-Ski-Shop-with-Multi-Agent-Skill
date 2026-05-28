package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record ProductPerformanceResponse(
        String productId,
        String category,
        Map<String, Object> metrics,
        DataAvailability availability,
        Instant analyzedAt
) {
    public ProductPerformanceResponse(String productId, String category, Map<String, Object> metrics, Instant analyzedAt) {
        this(productId, category, metrics, DataAvailability.available(), analyzedAt);
    }
}
