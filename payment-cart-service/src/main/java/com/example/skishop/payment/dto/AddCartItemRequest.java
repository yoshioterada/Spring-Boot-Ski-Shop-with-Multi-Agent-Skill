package com.example.skishop.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record AddCartItemRequest(
        @NotBlank(message = "商品IDは必須です")
        String productId,

        @NotBlank(message = "商品名は必須です")
        @Size(max = 200)
        String productName,

        @Min(value = 1, message = "数量は1以上である必要があります")
        int quantity,

        @NotNull(message = "単価は必須です")
        @DecimalMin("0")
        BigDecimal unitPrice
) {}
