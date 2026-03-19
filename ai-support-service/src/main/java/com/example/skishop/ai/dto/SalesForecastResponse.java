package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.List;

public record SalesForecastResponse(
        String productId,
        String forecastPeriod,
        int horizon,
        List<ForecastEntry> forecasts,
        String algorithm,
        String modelVersion,
        double accuracy,
        Instant generatedAt
) {
    public record ForecastEntry(
            Instant periodStart,
            Instant periodEnd,
            double predictedDemand,
            double confidenceInterval,
            double seasonalFactor,
            double trendFactor
    ) {}
}
