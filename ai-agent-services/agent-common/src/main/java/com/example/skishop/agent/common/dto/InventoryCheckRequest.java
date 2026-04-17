package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InventoryCheckRequest(
        @NotEmpty List<String> productIds,
        int requiredQuantity
) {
    public InventoryCheckRequest {
        if (requiredQuantity <= 0) requiredQuantity = 1;
    }
}
