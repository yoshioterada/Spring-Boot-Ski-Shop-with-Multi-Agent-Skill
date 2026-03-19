package com.example.skishop.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        String brand,
        String categoryId,
        BigDecimal regularPrice,
        BigDecimal salePrice,
        String currency,
        int stockQuantity,
        int availableQuantity,
        String status,
        Map<String, String> attributes,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}
