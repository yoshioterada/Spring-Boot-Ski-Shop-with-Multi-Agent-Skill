package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.List;

/**
 * F2 季節予測レスポンス (spec § 4.2.3).
 * 数値はすべて Step 1 (Java) で確定。LLM は narrative + assumptions のみ生成。
 */
public record SeasonalForecastResponse(
        String horizonLabel,
        Instant generatedAt,
        List<CategoryForecast> categories,
        String narrative,
        List<String> assumptions
) {
    public record CategoryForecast(
            String categoryId,
            String categoryName,
            int predictedDemandUnits,
            int confidenceLow,
            int confidenceHigh,
            double yoyGrowth,
            List<SkuForecast> topSkus
    ) {}

    public record SkuForecast(
            String sku,
            int predictedUnits,
            int stockNow,
            int recommendOrder
    ) {}
}
