package com.example.skishop.usermanagement.dto;

public record CouponSummary(
        String couponId,
        String couponCode,
        String couponType,
        String discountType,
        String discountValue,
        String minimumAmount,
        String expiresAt
) {}
