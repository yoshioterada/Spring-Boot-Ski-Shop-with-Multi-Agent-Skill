package com.example.skishop.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateShipmentRequest(
        @NotNull(message = "注文IDは必須です")
        UUID orderId,

        @NotBlank(message = "配送業者は必須です")
        String carrier,

        String trackingNumber
) {}
