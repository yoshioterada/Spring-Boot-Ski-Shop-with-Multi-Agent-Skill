package com.example.skishop.point.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_transactions")
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false)
    private int points;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 500)
    private String description;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_expired", nullable = false)
    private boolean expired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected PointTransaction() {}

    public PointTransaction(UUID userId, TransactionType type, int points, int balanceAfter, String description, String referenceId) {
        this.userId = userId;
        this.type = type;
        this.points = points;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.referenceId = referenceId;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public TransactionType getType() { return type; }
    public int getPoints() { return points; }
    public int getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public String getReferenceId() { return referenceId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isExpired() { return expired; }

    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public enum TransactionType {
        EARNED, REDEEMED, EXPIRED, TRANSFERRED_IN, TRANSFERRED_OUT
    }
}
