package com.example.skishop.sales.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "returns")
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "return_number", nullable = false, unique = true, length = 50)
    private String returnNumber;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column
    private Integer quantity;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private java.math.BigDecimal refundAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReturnRequest() {}

    public ReturnRequest(String returnNumber, UUID orderId, UUID customerId, String reason) {
        this.returnNumber = returnNumber;
        this.orderId = orderId;
        this.customerId = customerId;
        this.reason = reason;
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
    public String getReturnNumber() { return returnNumber; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public String getReason() { return reason; }
    public ReturnStatus getStatus() { return status; }
    public Integer getQuantity() { return quantity; }
    public java.math.BigDecimal getRefundAmount() { return refundAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(ReturnStatus status) { this.status = status; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setRefundAmount(java.math.BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public enum ReturnStatus {
        REQUESTED, APPROVED, REJECTED, RECEIVED, REFUNDED
    }
}
