package com.example.skishop.agent.orchestrator.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuoteSummary(
        String orderId,
        List<QuoteItem> items,
        BigDecimal subtotal,
        BigDecimal couponDiscount,
        BigDecimal pointDiscount,
        BigDecimal totalAmount,
        String reservationId,
        Instant reservationExpiresAt
) {
    public record QuoteItem(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String matchReason
    ) {}
}
