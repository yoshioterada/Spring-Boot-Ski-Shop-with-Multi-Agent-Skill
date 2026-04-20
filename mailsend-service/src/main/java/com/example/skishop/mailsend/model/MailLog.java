package com.example.skishop.mailsend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mail_logs")
public class MailLog {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MailStatus status;

    @Column(name = "azure_operation_id", length = 200)
    private String azureOperationId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "mailLog", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MailAttachment> attachments = new ArrayList<>();

    protected MailLog() {
    }

    public MailLog(String eventType,
                   String eventId,
                   String correlationId,
                   String recipientEmail,
                   String recipientName,
                   String templateName,
                   String subject,
                   MailStatus status,
                   String variablesJson) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.eventId = eventId;
        this.correlationId = correlationId;
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.templateName = templateName;
        this.subject = subject;
        this.status = status;
        this.variablesJson = variablesJson;
        this.retryCount = 0;
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getSubject() {
        return subject;
    }

    public MailStatus getStatus() {
        return status;
    }

    public String getAzureOperationId() {
        return azureOperationId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markSending() {
        this.status = MailStatus.SENDING;
    }

    public void markSent(String azureOperationId) {
        this.status = MailStatus.SENT;
        this.azureOperationId = azureOperationId;
        this.errorMessage = null;
        this.sentAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = MailStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void markSkipped(String reason) {
        this.status = MailStatus.SKIPPED;
        this.errorMessage = reason;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
