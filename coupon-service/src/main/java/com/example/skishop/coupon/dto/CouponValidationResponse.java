package com.example.skishop.coupon.dto;

import java.math.BigDecimal;

public record CouponValidationResponse(
        boolean valid,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String message
) {}
