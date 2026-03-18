package com.example.skishop.inventory.dto.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商品作成 Kafka イベント DTO。
 */
public record ProductCreatedEvent(
        Long productId,
        String sku,
        String name,
        String brand,
        Long categoryId,
        BigDecimal price,
        OffsetDateTime occurredAt
) {
}
