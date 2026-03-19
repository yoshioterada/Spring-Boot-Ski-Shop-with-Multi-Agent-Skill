package com.example.skishop.coupon.dto;

import java.util.List;

public record BulkGenerationResponse(
        int requestedCount,
        int generatedCount,
        List<String> codes
) {}
