package com.example.skishop.inventory.dto.event;

import java.time.OffsetDateTime;

/**
 * 在庫更新 Kafka イベント DTO。
 */
public record InventoryUpdatedEvent(
        Long inventoryId,
        Long productId,
        String productSku,
        String warehouseId,
        Integer stockQuantity,
        Integer availableQuantity,
        Integer reservedQuantity,
        OffsetDateTime occurredAt
) {
}
