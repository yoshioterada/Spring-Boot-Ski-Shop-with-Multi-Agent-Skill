package com.example.skishop.coupon.dto;

import com.example.skishop.coupon.model.Coupon.CouponType;
import com.example.skishop.coupon.model.Coupon.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        UUID campaignId,
        String code,
        CouponType couponType,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumAmount,
        BigDecimal maximumDiscount,
        int usageLimit,
        int usedCount,
        boolean active,
        Instant expiresAt,
        Instant createdAt
) {}
