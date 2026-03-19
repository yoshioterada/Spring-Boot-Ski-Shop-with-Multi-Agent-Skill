package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatFeedbackRequest(
        @NotBlank(message = "セッションIDは必須です")
        String sessionId,

        double satisfactionScore,

        String comment
) {}
