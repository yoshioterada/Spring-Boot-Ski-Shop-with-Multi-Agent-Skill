package com.example.skishop.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StockInRequest(
        @NotBlank(message = "商品IDは必須です")
        String productId,

        @Min(value = 1, message = "数量は1以上である必要があります")
        int quantity,

        String locationCode
) {}
