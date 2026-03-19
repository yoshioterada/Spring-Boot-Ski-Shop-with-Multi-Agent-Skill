package com.example.skishop.point.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tier_definitions")
public class TierDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier_level", nullable = false, unique = true, length = 20)
    private TierLevel level;

    @Column(name = "min_points", nullable = false)
    private int minPoints;

    @Column(name = "point_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal pointMultiplier = BigDecimal.ONE;

    @Column(name = "tier_name", length = 50)
    private String tierName;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> benefits = Map.of();

    protected TierDefinition() {}

    public TierDefinition(TierLevel level, int minPoints, BigDecimal pointMultiplier, String description) {
        this.level = level;
        this.minPoints = minPoints;
        this.pointMultiplier = pointMultiplier;
        this.description = description;
    }

    public UUID getId() { return id; }
    public TierLevel getLevel() { return level; }
    public int getMinPoints() { return minPoints; }
    public BigDecimal getPointMultiplier() { return pointMultiplier; }
    public String getTierName() { return tierName; }
    public String getDescription() { return description; }
    public Map<String, Object> getBenefits() { return benefits; }

    public enum TierLevel {
        BRONZE, SILVER, GOLD, PLATINUM
    }
}
