package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CouponOptimizationResult(
        String userId,
        String orderId,
        List<CouponCandidate> appliedCoupons,
        int appliedPoints,
        BigDecimal pointsDiscount,
        BigDecimal couponDiscountTotal,
        BigDecimal cartTotalBeforeDiscount,
        BigDecimal cartTotalAfterDiscount,
        BigDecimal totalSavings,
        String optimizationSummary,
        Instant calculatedAt
) {}
