package com.example.skishop.agent.common.dto;

import java.time.Instant;

public record InventoryAlert(
        String alertId,
        String productId,
        String severity,        // "INFO" | "WARNING" | "CRITICAL"
        String message,
        int currentStock,
        int threshold,
        Instant generatedAt
) {}
