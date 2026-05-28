package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record CustomReportResponse(
        String reportId,
        String reportType,
        Map<String, Object> data,
        DataAvailability availability,
        Instant generatedAt
) {
    public CustomReportResponse(String reportId, String reportType, Map<String, Object> data, Instant generatedAt) {
        this(reportId, reportType, data, DataAvailability.available(), generatedAt);
    }
}
