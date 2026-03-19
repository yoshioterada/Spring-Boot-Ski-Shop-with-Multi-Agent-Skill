package com.example.skishop.ai.dto;

import com.example.skishop.ai.model.ChatSession.SessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateChatSessionRequest(
        @NotBlank(message = "ユーザーIDは必須です")
        String userId,

        @NotNull(message = "セッションタイプは必須です")
        SessionType sessionType
) {}
