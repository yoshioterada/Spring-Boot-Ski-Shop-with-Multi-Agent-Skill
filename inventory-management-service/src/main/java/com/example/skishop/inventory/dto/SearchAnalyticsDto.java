package com.example.skishop.inventory.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 検索分析タブ用 DTO 群。
 */
public final class SearchAnalyticsDto {

    private SearchAnalyticsDto() {}

    public record SearchAnalyticsSummary(
            int days,
            long totalSearches,
            long uniqueKeywords,
            double zeroHitRatio,
            double averageDurationMs,
            List<PopularKeyword> popularKeywords,
            List<DailySearchPoint> trend
    ) {}

    public record PopularKeyword(
            String keyword,
            long searches,
            long totalHits,
            double avgHitRate
    ) {}

    public record DailySearchPoint(
            LocalDate date,
            long searches,
            long zeroHits,
            double avgDurationMs
    ) {}
}
