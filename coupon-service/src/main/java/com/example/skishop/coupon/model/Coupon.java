package com.example.skishop.coupon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 50)
    private CouponType couponType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "minimum_amount", precision = 10, scale = 2)
    private BigDecimal minimumAmount = BigDecimal.ZERO;

    @Column(name = "maximum_discount", precision = 10, scale = 2)
    private BigDecimal maximumDiscount;

    @Column(name = "usage_limit", nullable = false)
    private int usageLimit = 1;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Coupon() {}

    public Coupon(Campaign campaign, String code, CouponType couponType,
                  DiscountType discountType, BigDecimal discountValue,
                  BigDecimal minimumAmount, BigDecimal maximumDiscount,
                  int usageLimit, Instant expiresAt) {
        this.campaign = campaign;
        this.code = code;
        this.couponType = couponType;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minimumAmount = minimumAmount != null ? minimumAmount : BigDecimal.ZERO;
        this.maximumDiscount = maximumDiscount;
        this.usageLimit = usageLimit;
        this.usedCount = 0;
        this.active = true;
        this.expiresAt = expiresAt;
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

    public boolean isUsable() {
        return active
                && usedCount < usageLimit
                && Instant.now().isBefore(expiresAt);
    }

    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        if (minimumAmount != null && orderAmount.compareTo(minimumAmount) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = switch (discountType) {
            case PERCENTAGE -> orderAmount.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            case FIXED -> discountValue;
        };
        if (maximumDiscount != null && discount.compareTo(maximumDiscount) > 0) {
            discount = maximumDiscount;
        }
        return discount;
    }

    public void incrementUsedCount() {
        this.usedCount++;
    }

    public UUID getId() { return id; }
    public Campaign getCampaign() { return campaign; }
    public String getCode() { return code; }
    public CouponType getCouponType() { return couponType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public BigDecimal getMaximumDiscount() { return maximumDiscount; }
    public int getUsageLimit() { return usageLimit; }
    public int getUsedCount() { return usedCount; }
    public boolean isActive() { return active; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setActive(boolean active) { this.active = active; }

    public enum CouponType {
        PERCENTAGE, FIXED_AMOUNT, FREE_SHIPPING
    }

    public enum DiscountType {
        PERCENTAGE, FIXED
    }
}
