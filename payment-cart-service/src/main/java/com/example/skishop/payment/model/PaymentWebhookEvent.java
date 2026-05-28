package com.example.skishop.payment.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_payment_webhook_provider_event", columnNames = {"provider", "event_id"}))
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 150)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessingStatus status = ProcessingStatus.RECEIVED;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected PaymentWebhookEvent() {}

    public PaymentWebhookEvent(String provider, String eventId, String eventType, String payloadHash) {
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payloadHash = payloadHash;
    }

    @PrePersist
    protected void onCreate() {
        this.receivedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public UUID getPaymentId() { return paymentId; }
    public ProcessingStatus getStatus() { return status; }

    public void markProcessed(UUID paymentId) {
        this.paymentId = paymentId;
        this.status = ProcessingStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = Instant.now();
    }

    public void markIgnored(UUID paymentId, String reason) {
        this.paymentId = paymentId;
        this.status = ProcessingStatus.IGNORED;
        this.errorMessage = reason;
        this.processedAt = Instant.now();
    }

    public enum ProcessingStatus {
        RECEIVED, PROCESSED, FAILED, IGNORED
    }
}
