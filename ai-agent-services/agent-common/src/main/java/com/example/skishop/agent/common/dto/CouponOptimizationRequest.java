package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CouponOptimizationRequest(
        @NotBlank String userId,
        @NotBlank String orderId,
        @NotEmpty List<CartItemPricing> cartItems,
        String customerTier,
        boolean usePoints,
        String couponCode
) {}
