package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;

public record CartItemPricing(
        String productId,
        String productName,
        String category,
        int quantity,
        BigDecimal dynamicUnitPrice,
        BigDecimal lineTotal
) {}
