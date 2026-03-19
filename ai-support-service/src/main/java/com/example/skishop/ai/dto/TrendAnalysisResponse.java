package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TrendAnalysisResponse(
        String category,
        String timeframe,
        List<Map<String, Object>> trends,
        Instant analyzedAt
) {}
