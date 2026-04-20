package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.List;

/**
 * F5 機会発見レーダー レスポンス (spec § 20.5.1).
 */
public record ZeroHitOpportunityResponse(
        Instant generatedAt,
        int periodDays,
        Summary summary,
        List<Opportunity> opportunities
) {
    public record Summary(
            int totalZeroHitQueries,
            long totalSearchVolume,
            long estimatedTotalLossJpy,
            String topCategoryGap
    ) {}

    public record Opportunity(
            int rank,
            String normalizedKeyword,
            long searchCount,
            Instant lastSearchedAt,
            String estimatedCategory,
            String estimatedGenderTarget,
            long estimatedLossJpy,
            String priority,
            String narrative,
            List<BrandSuggestion> suggestedBrands,
            List<String> relatedExistingSkus
    ) {}

    public record BrandSuggestion(String brand, String model) {}
}
