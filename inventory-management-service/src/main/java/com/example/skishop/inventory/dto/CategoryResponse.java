package com.example.skishop.inventory.dto;

import java.time.Instant;
import java.util.List;

public record CategoryResponse(
        String id,
        String name,
        String description,
        String parentId,
        List<String> childIds,
        String imageUrl,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
