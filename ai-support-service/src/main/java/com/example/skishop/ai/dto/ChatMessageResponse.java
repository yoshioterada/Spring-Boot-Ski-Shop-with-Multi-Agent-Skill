package com.example.skishop.ai.dto;

public record ChatMessageResponse(
        String sessionId,
        String messageId,
        String content,
        String role
) {}
