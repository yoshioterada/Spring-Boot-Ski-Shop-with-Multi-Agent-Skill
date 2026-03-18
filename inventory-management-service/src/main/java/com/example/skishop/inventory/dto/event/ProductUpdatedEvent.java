package com.example.skishop.inventory.dto.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商品更新 Kafka イベント DTO。
 */
public record ProductUpdatedEvent(
        Long productId,
        String sku,
        String name,
        BigDecimal price,
        Boolean isActive,
        OffsetDateTime occurredAt
) {
}
