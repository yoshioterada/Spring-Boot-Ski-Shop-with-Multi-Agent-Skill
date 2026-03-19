package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.Map;

public record CustomReportResponse(
        String reportId,
        String reportType,
        Map<String, Object> data,
        Instant generatedAt
) {}
