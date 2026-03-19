package com.example.skishop.sales.dto;

import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        UUID orderId,
        String carrier,
        String trackingNumber,
        String status,
        Instant shippedAt,
        Instant deliveredAt,
        Instant createdAt
) {}
