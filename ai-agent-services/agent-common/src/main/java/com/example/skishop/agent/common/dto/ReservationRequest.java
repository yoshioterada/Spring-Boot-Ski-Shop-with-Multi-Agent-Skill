package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReservationRequest(
        @NotBlank String orderId,
        @NotBlank String userId,
        @NotEmpty List<ReservationItem> items,
        int reservationTtlMinutes
) {
    public ReservationRequest {
        if (reservationTtlMinutes <= 0) reservationTtlMinutes = 30;
    }

    public record ReservationItem(String productId, int quantity) {}
}
