package com.example.skishop.ai.dto;

import java.time.Instant;
import java.util.List;

/**
 * F4 滞留在庫レーダーレスポンス (spec § 19.4).
 */
public record DeadStockResponse(
        Instant generatedAt,
        List<DeadStockItem> items,
        String narrative
) {
    public enum Severity { CRITICAL, HIGH, MEDIUM }

    public record DeadStockItem(
            String sku,
            String name,
            int stock,
            long sales30,
            long sales90,
            int daysOfSupply,
            Severity severity,
            double suggestedDiscountPct,
            String aiReason
    ) {}
}
