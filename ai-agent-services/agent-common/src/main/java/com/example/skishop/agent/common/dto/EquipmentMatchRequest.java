package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record EquipmentMatchRequest(
        @NotBlank String userId,
        String skillLevel,
        BodyMeasurements bodyMeasurements,
        List<String> desiredCategories,
        Integer budgetYen,
        String destination,
        boolean includeRental,
        boolean includePurchase,
        Integer quantity
) {
    public EquipmentMatchRequest {
        if (quantity == null || quantity <= 0) quantity = 1;
        if (desiredCategories == null) desiredCategories = List.of();
    }
}
