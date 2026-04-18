package com.example.skishop.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code POST /api/v1/cart/build} のレスポンス。
 */
public record BuildCartResponse(
        String orderId,
        String userId,
        BigDecimal subtotal,
        BigDecimal couponDiscount,
        BigDecimal pointDiscount,
        BigDecimal totalAmount,
        String status,
        List<BuildCartLine> lines,
        Instant createdAt
) {
    public record BuildCartLine(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}
}
