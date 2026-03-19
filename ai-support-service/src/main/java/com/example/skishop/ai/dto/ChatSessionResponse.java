package com.example.skishop.ai.dto;

import com.example.skishop.ai.model.ChatMessage;
import com.example.skishop.ai.model.ChatSession.SessionStatus;
import com.example.skishop.ai.model.ChatSession.SessionType;

import java.time.Instant;
import java.util.List;

public record ChatSessionResponse(
        String id,
        String userId,
        SessionType sessionType,
        SessionStatus status,
        List<ChatMessage> messages,
        Instant createdAt,
        Instant lastActivity
) {}
