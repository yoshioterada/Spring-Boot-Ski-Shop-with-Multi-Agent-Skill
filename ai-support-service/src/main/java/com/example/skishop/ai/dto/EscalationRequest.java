package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record EscalationRequest(
        @NotBlank(message = "セッションIDは必須です")
        String sessionId,

        String reason,

        String priority
) {}
