package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;

public record PriceBreakdown(
        BigDecimal basePrice,
        double demandMultiplier,
        String demandLevel,            // "VERY_HIGH" | "HIGH" | "NORMAL" | "LOW"
        double weatherMultiplier,
        String weatherCondition,       // "EXCELLENT" | "GOOD" | "POOR"
        double inventoryMultiplier,
        String inventoryStatus,        // "SCARCE" | "NORMAL" | "ABUNDANT"
        double customerTierDiscount,
        String customerTier,
        BigDecimal finalPrice
) {}
