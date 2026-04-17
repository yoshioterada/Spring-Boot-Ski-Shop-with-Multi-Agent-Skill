package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PricingRequest(
        @NotBlank String productId,
        @NotBlank String userId,
        String customerTier,
        String resortLocation,
        @Positive int quantity
) {
    public PricingRequest {
        if (customerTier == null) customerTier = "BRONZE";
        if (quantity <= 0) quantity = 1;
    }
}
