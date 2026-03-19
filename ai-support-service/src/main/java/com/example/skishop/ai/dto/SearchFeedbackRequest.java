package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record SearchFeedbackRequest(
        @NotBlank(message = "クエリは必須です")
        String query,

        String resultId,

        boolean relevant,

        String userId
) {}
