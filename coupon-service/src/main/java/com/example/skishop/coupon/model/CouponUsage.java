package com.example.skishop.coupon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon_usage")
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "discount_applied", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountApplied;

    @Column(name = "order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;

    protected CouponUsage() {}

    public CouponUsage(Coupon coupon, UUID userId, UUID orderId,
                       BigDecimal discountApplied, BigDecimal orderAmount) {
        this.coupon = coupon;
        this.userId = userId;
        this.orderId = orderId;
        this.discountApplied = discountApplied;
        this.orderAmount = orderAmount;
    }

    @PrePersist
    protected void onCreate() {
        this.usedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Coupon getCoupon() { return coupon; }
    public UUID getUserId() { return userId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getDiscountApplied() { return discountApplied; }
    public BigDecimal getOrderAmount() { return orderAmount; }
    public Instant getUsedAt() { return usedAt; }
}
