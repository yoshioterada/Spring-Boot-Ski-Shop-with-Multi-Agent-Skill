package com.example.skishop.point.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_tiers")
public class UserTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_tier", nullable = false, length = 20)
    private TierDefinition.TierLevel currentTier = TierDefinition.TierLevel.BRONZE;

    @Column(name = "current_balance", nullable = false)
    private int currentBalance;

    @Column(name = "total_earned", nullable = false)
    private int totalEarned;

    @Column(name = "total_redeemed", nullable = false)
    private int totalRedeemed;

    @Column(name = "tier_upgraded_at")
    private Instant tierUpgradedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected UserTier() {}

    public UserTier(UUID userId, TierDefinition.TierLevel tierLevel) {
        this.userId = userId;
        this.currentTier = tierLevel;
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

    public void addPoints(int points) {
        this.currentBalance += points;
        this.totalEarned += points;
    }

    public void redeemPoints(int points) {
        this.currentBalance -= points;
        this.totalRedeemed += points;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public TierDefinition.TierLevel getCurrentTier() { return currentTier; }
    public int getCurrentBalance() { return currentBalance; }
    public int getTotalEarned() { return totalEarned; }
    public int getTotalRedeemed() { return totalRedeemed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getTierUpgradedAt() { return tierUpgradedAt; }

    public void setCurrentTier(TierDefinition.TierLevel currentTier) { this.currentTier = currentTier; }
    public void setTierUpgradedAt(Instant tierUpgradedAt) { this.tierUpgradedAt = tierUpgradedAt; }
}
