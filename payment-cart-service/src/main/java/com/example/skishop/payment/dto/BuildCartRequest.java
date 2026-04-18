package com.example.skishop.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * Orchestrator から呼び出される {@code POST /api/v1/cart/build} のリクエスト。
 * Multi-Agent が構築済みのカート明細・適用済み割引額を受け取り、payment-cart 側で正規化・確定する。
 */
public record BuildCartRequest(
        @NotBlank String userId,
        @NotBlank String orderId,
        @NotEmpty @Valid List<BuildCartItem> items,
        @NotNull @PositiveOrZero BigDecimal couponDiscount,
        @NotNull @PositiveOrZero BigDecimal pointDiscount
) {
    public record BuildCartItem(
            @NotBlank String productId,
            @NotBlank String productName,
            @NotNull @PositiveOrZero Integer quantity,
            @NotNull @PositiveOrZero BigDecimal dynamicUnitPrice,
            @NotNull @PositiveOrZero BigDecimal lineTotal
    ) {}
}
