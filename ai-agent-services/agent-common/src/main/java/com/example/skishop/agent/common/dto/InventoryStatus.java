package com.example.skishop.agent.common.dto;

import java.util.List;

public record InventoryStatus(
        String productId,
        String productName,
        int stockQuantity,
        String availabilityStatus,   // "AVAILABLE" | "LOW_STOCK" | "OUT_OF_STOCK"
        boolean isReservable,
        String estimatedRestockDate, // ISO-8601 date
        List<String> alternativeProductIds,
        InventoryAlert alert
) {}
