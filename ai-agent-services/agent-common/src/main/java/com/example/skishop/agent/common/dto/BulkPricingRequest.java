package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkPricingRequest(
        @NotBlank String userId,
        String customerTier,
        @NotEmpty List<BulkPricingItem> items,
        String resortLocation
) {
    public record BulkPricingItem(
            @NotBlank String productId,
            int quantity
    ) {}
}
