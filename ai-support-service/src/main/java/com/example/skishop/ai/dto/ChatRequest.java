package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * F1 チャットリクエスト (spec § 4.2.1).
 */
public record ChatRequest(
        @Nullable String sessionId,
        @NotBlank @Size(max = 2000) String message,
        @Nullable Map<String, Object> context
) {}
