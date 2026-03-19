package com.example.skishop.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReturnResponse(
        UUID id,
        String returnNumber,
        UUID orderId,
        UUID customerId,
        String reason,
        String status,
        Integer quantity,
        BigDecimal refundAmount,
        Instant createdAt,
        Instant updatedAt
) {}
