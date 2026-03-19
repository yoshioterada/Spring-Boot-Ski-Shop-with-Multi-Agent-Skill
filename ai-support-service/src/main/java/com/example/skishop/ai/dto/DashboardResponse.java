package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record DashboardResponse(
        String dashboardType,
        Map<String, Object> data,
        Instant generatedAt
) {}
