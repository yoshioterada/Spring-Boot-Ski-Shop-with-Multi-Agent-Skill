package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record DashboardResponse(
        String dashboardType,
        Map<String, Object> data,
        DataAvailability availability,
        Instant generatedAt
) {
    public DashboardResponse(String dashboardType, Map<String, Object> data, Instant generatedAt) {
        this(dashboardType, data, DataAvailability.available(), generatedAt);
    }
}
