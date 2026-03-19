package com.example.skishop.mailsend.dto;

import com.example.skishop.mailsend.model.MailLog;

import java.time.Instant;
import java.util.UUID;

public record MailLogResponse(
        UUID id,
        String eventType,
        String recipientEmail,
        String recipientName,
        String templateName,
        String subject,
        String status,
        int retryCount,
        String errorMessage,
        Instant sentAt,
        Instant createdAt
) {
    public static MailLogResponse from(MailLog log) {
        return new MailLogResponse(
                log.getId(),
                log.getEventType(),
                log.getRecipientEmail(),
                log.getRecipientName(),
                log.getTemplateName(),
                log.getSubject(),
                log.getStatus().name(),
                log.getRetryCount(),
                log.getErrorMessage(),
                log.getSentAt(),
                log.getCreatedAt()
        );
    }
}
