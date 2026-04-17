package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingResult(
        String productId,
        String userId,
        BigDecimal finalPrice,
        BigDecimal originalBasePrice,
        double totalDiscountRate,
        BigDecimal savingsAmount,
        PriceBreakdown breakdown,
        String priceJustification,
        Instant calculatedAt,
        Instant validUntil
) {}
