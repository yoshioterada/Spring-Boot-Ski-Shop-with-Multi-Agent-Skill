package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record SearchAnalyticsResponse(
        long totalSearches,
        long searchesWithResults,
        double averageResponseTimeMs,
        Map<String, Long> topQueries,
        Map<String, Long> searchesByType,
        Instant analyzedAt
) {}
