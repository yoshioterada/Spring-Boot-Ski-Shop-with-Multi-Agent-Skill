package com.example.skishop.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UpdateProductRequest(
        @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        @Size(max = 100)
        String brand,

        String categoryId,

        @DecimalMin(value = "0", message = "価格は0以上である必要があります")
        BigDecimal regularPrice,

        Map<String, String> attributes,

        List<String> tags
) {}
