package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ProductCandidate(
        String productId,
        String productName,
        String category,
        String brand,
        BigDecimal basePrice,
        boolean isAvailable,
        int stockQuantity,
        String skillLevelSuitability,
        String weatherSuitability,
        Map<String, String> attributes
) {}
