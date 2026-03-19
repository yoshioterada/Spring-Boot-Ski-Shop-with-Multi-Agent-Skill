package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank(message = "セッションIDは必須です")
        String sessionId,

        @NotBlank(message = "メッセージは必須です")
        String message
) {}
