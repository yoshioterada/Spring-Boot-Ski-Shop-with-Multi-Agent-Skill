package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponCandidate(
        String couponId,
        String couponCode,
        String couponType,         // "PERCENTAGE" | "FIXED_AMOUNT" | "FREE_SHIPPING" | "BUNDLE"
        BigDecimal discountRate,
        BigDecimal discountAmount,
        BigDecimal minimumOrder,
        String applicableCategory,
        LocalDate expiresAt,
        boolean isStackable,
        int usageLimit
) {}
