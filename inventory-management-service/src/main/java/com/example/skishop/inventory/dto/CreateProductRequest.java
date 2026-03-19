package com.example.skishop.inventory.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CreateProductRequest(
        @NotBlank(message = "SKUは必須です")
        @Size(max = 50)
        String sku,

        @NotBlank(message = "商品名は必須です")
        @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        @Size(max = 100)
        String brand,

        String categoryId,

        @NotNull(message = "価格は必須です")
        @DecimalMin(value = "0", message = "価格は0以上である必要があります")
        BigDecimal regularPrice,

        @Min(value = 0)
        int initialStock,

        String locationCode,

        Map<String, String> attributes,

        List<String> tags
) {}
