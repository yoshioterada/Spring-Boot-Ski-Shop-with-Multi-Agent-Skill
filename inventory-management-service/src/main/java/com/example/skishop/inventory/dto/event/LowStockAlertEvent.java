package com.example.skishop.inventory.dto.event;

import java.time.OffsetDateTime;

/**
 * 在庫不足アラート Kafka イベント DTO。
 */
public record LowStockAlertEvent(
        Long inventoryId,
        Long productId,
        String productSku,
        String productName,
        String warehouseId,
        Integer availableQuantity,
        Integer reorderLevel,
        OffsetDateTime occurredAt
) {
}
