package com.example.skishop.agent.common.dto;

import java.time.Instant;
import java.util.List;

public record ReservationResult(
        String reservationId,
        String orderId,
        boolean isFullyReserved,
        List<String> reservedProductIds,
        List<String> failedProductIds,
        Instant expiresAt
) {}
