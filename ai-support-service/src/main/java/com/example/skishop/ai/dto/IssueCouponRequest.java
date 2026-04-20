package com.example.skishop.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * F4 クーポン発行リクエスト (spec § 19.4).
 */
public record IssueCouponRequest(
        @DecimalMin("0.0") @DecimalMax("40.0")
        double discountPct,
        @Size(max = 500)
        String memo
) {}
