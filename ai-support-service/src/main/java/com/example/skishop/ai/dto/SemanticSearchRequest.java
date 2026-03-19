package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequest(
        @NotBlank(message = "クエリは必須です")
        String query,

        String category,

        String userId,

        int limit
) {
    public SemanticSearchRequest {
        if (limit <= 0) limit = 10;
    }
}
