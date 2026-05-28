package com.example.skishop.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "payment_intent_id", unique = true, length = 100)
    private String paymentIntentId;

    @Column(name = "gateway_payment_id", length = 150)
    private String gatewayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "JPY";

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(name = "raw_gateway_status", length = 100)
    private String rawGatewayStatus;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "gateway_provider", length = 50)
    private String gatewayProvider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Payment() {}

    public Payment(UUID userId, BigDecimal amount, String paymentMethod) {
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentIntentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getOrderId() { return orderId; }
    public String getPaymentIntentId() { return paymentIntentId; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getGatewayResponse() { return gatewayResponse; }
    public String getRawGatewayStatus() { return rawGatewayStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getFailureReason() { return failureReason; }
    public String getFailureCode() { return failureCode; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getAuthorizedAt() { return authorizedAt; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getFailedAt() { return failedAt; }
    public String getGatewayProvider() { return gatewayProvider; }

    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public void setPaymentIntentId(String paymentIntentId) { this.paymentIntentId = paymentIntentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setGatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; }
    public void setRawGatewayStatus(String rawGatewayStatus) { this.rawGatewayStatus = rawGatewayStatus; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setAuthorizedAt(Instant authorizedAt) { this.authorizedAt = authorizedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }
    public void setGatewayProvider(String gatewayProvider) { this.gatewayProvider = gatewayProvider; }

    public enum PaymentStatus {
        PENDING, REQUIRES_ACTION, AUTHORIZED, CAPTURED, FAILED, CANCELLED, PARTIALLY_REFUNDED, REFUNDED
    }
}
