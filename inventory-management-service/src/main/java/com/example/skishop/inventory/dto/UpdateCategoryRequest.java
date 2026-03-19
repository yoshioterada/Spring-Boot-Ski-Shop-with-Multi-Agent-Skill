package com.example.skishop.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank(message = "カテゴリ名は必須です")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        String imageUrl,

        Integer sortOrder,

        Boolean active
) {}
