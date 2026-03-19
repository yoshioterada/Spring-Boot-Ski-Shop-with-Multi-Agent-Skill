package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CustomReportRequest(
        @NotBlank(message = "レポートタイプは必須です")
        String reportType,

        Map<String, Object> parameters,

        String dateRange
) {}
