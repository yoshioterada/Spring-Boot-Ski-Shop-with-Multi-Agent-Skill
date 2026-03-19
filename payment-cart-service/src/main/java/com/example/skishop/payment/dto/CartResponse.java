package com.example.skishop.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        UUID userId,
        BigDecimal totalAmount,
        String currency,
        List<CartItemResponse> items,
        Instant updatedAt
) {
    public record CartItemResponse(
            UUID id,
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {}
}
