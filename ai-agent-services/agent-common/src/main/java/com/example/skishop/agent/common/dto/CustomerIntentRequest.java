package com.example.skishop.agent.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerIntentRequest(
        @NotBlank String userId,

        @NotBlank
        @Size(max = 2000, message = "リクエストは2000文字以内で入力してください")
        String userMessage,

        String sessionId,
        String locale
) {}
