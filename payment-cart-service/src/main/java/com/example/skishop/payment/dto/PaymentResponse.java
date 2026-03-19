package com.example.skishop.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID userId,
        UUID orderId,
        String paymentIntentId,
        String status,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String gatewayProvider,
        BigDecimal refundedAmount,
        Instant completedAt,
        Instant createdAt
) {}
