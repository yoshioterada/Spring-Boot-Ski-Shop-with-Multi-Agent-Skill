package com.example.skishop.coupon.dto;

import com.example.skishop.coupon.model.Coupon.CouponType;
import com.example.skishop.coupon.model.Coupon.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BulkGenerationRequest(
        @NotNull(message = "キャンペーンIDは必須です")
        UUID campaignId,

        @NotNull(message = "クーポンタイプは必須です")
        CouponType couponType,

        @NotNull(message = "割引タイプは必須です")
        DiscountType discountType,

        @NotNull(message = "割引値は必須です")
        @DecimalMin(value = "0.01", message = "割引値は0より大きい値を指定してください")
        BigDecimal discountValue,

        @DecimalMin(value = "0", message = "最低金額は0以上を指定してください")
        BigDecimal minimumAmount,

        BigDecimal maximumDiscount,

        @Min(value = 1, message = "使用回数は1以上を指定してください")
        int usageLimit,

        @NotNull(message = "有効期限は必須です")
        Instant expiresAt,

        @Min(value = 1, message = "生成数は1以上を指定してください")
        int count,

        String prefix
) {}
