package com.example.skishop.coupon.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_coupons")
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "is_redeemed", nullable = false)
    private boolean redeemed;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Version
    private Long version;

    protected UserCoupon() {}

    public UserCoupon(UUID userId, Coupon coupon) {
        this.userId = userId;
        this.coupon = coupon;
        this.redeemed = false;
    }

    @PrePersist
    protected void onCreate() {
        this.assignedAt = Instant.now();
    }

    public void markRedeemed() {
        this.redeemed = true;
        this.redeemedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Coupon getCoupon() { return coupon; }
    public boolean isRedeemed() { return redeemed; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getRedeemedAt() { return redeemedAt; }
}
