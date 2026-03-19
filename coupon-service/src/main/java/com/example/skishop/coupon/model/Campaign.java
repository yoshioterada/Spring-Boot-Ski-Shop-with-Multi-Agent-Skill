package com.example.skishop.coupon.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false, length = 50)
    private CampaignType campaignType;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "rules", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> rules = Map.of();

    @Column(name = "max_coupons")
    private Integer maxCoupons;

    @Column(name = "generated_coupons", nullable = false)
    private int generatedCoupons;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Coupon> coupons = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Campaign() {}

    public Campaign(String name, String description, CampaignType campaignType,
                    Instant startDate, Instant endDate, Integer maxCoupons) {
        this.name = name;
        this.description = description;
        this.campaignType = campaignType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxCoupons = maxCoupons;
        this.active = false;
        this.generatedCoupons = 0;
        this.rules = Map.of();
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

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void incrementGeneratedCoupons() {
        this.generatedCoupons++;
    }

    public boolean isCurrentlyActive() {
        Instant now = Instant.now();
        return active && now.isAfter(startDate) && now.isBefore(endDate);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CampaignType getCampaignType() { return campaignType; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public boolean isActive() { return active; }
    public Map<String, Object> getRules() { return rules; }
    public Integer getMaxCoupons() { return maxCoupons; }
    public int getGeneratedCoupons() { return generatedCoupons; }
    public List<Coupon> getCoupons() { return coupons; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public void setMaxCoupons(Integer maxCoupons) { this.maxCoupons = maxCoupons; }
    public void setRules(Map<String, Object> rules) { this.rules = (rules == null ? Map.of() : rules); }

    public enum CampaignType {
        PERCENTAGE, FIXED_AMOUNT, BOGO, FREE_SHIPPING
    }
}
