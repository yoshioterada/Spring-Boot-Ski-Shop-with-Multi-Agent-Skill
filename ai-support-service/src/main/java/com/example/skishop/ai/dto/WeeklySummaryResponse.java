package com.example.skishop.ai.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * F3 週次サマリーレスポンス DTO (spec § 4.2.2 完全準拠).
 */
public record WeeklySummaryResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        Instant generatedAt,
        boolean cacheHit,
        Kpis kpis,
        List<Highlight> highlights,
        String narrative,
        List<ProductMovement> topRisingProducts,
        List<ProductMovement> topFallingProducts
) {
    public record Kpis(
            KpiValue revenue,
            KpiValue orders,
            KpiValue uniqueCustomers,
            KpiValue avgOrderValue
    ) {}

    public record KpiValue(
            long value,
            double wow,
            double yoy
    ) {}

    public record Highlight(
            String icon,
            String text
    ) {}

    public record ProductMovement(
            String sku,
            String name,
            long sales,
            double changeRate
    ) {}
}
