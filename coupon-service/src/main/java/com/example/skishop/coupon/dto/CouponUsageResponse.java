package com.example.skishop.coupon.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponUsageResponse(
        UUID id,
        UUID couponId,
        String couponCode,
        UUID userId,
        UUID orderId,
        BigDecimal discountApplied,
        BigDecimal orderAmount,
        Instant usedAt
) {}
