package com.example.skishop.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponRedemptionRequest(
        @NotBlank(message = "クーポンコードは必須です")
        String code,

        @NotNull(message = "ユーザーIDは必須です")
        UUID userId,

        @NotNull(message = "注文IDは必須です")
        UUID orderId,

        @NotNull(message = "注文金額は必須です")
        BigDecimal orderAmount
) {}
