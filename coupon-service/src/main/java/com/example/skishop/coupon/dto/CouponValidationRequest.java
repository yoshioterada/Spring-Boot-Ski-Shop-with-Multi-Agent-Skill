package com.example.skishop.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponValidationRequest(
        @NotBlank(message = "クーポンコードは必須です")
        String code,

        @NotNull(message = "カート金額は必須です")
        BigDecimal cartAmount,

        @NotNull(message = "ユーザーIDは必須です")
        UUID userId
) {}
