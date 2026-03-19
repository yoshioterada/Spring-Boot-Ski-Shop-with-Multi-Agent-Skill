package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = "recommendations")
public class Recommendation {

    @Id
    private String id;
    private String userId;
    private RecommendationType type;
    private List<ProductRecommendation> products;
    private double confidenceScore;
    private String algorithm;
    private String version;
    private boolean clicked;
    private Instant clickedAt;
    private boolean purchased;
    private Instant purchasedAt;
    private String reason;
    private Map<String, Object> context;
    private List<String> features;
    private Instant expiresAt;
    private Instant createdAt;

    public Recommendation() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.clicked = false;
        this.purchased = false;
        this.features = List.of();
    }

    public Recommendation(String userId, RecommendationType type,
                          List<ProductRecommendation> products, double confidenceScore,
                          String algorithm) {
        this();
        this.userId = userId;
        this.type = type;
        this.products = products;
        this.confidenceScore = confidenceScore;
        this.algorithm = algorithm;
        this.version = "1.0";
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public RecommendationType getType() { return type; }
    public List<ProductRecommendation> getProducts() { return products; }
    public double getConfidenceScore() { return confidenceScore; }
    public String getAlgorithm() { return algorithm; }
    public String getVersion() { return version; }
    public boolean isClicked() { return clicked; }
    public Instant getClickedAt() { return clickedAt; }
    public String getReason() { return reason; }
    public Map<String, Object> getContext() { return context; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isPurchased() { return purchased; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public List<String> getFeatures() { return features; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setClicked(boolean clicked) { this.clicked = clicked; }
    public void setClickedAt(Instant clickedAt) { this.clickedAt = clickedAt; }
    public void setPurchased(boolean purchased) { this.purchased = purchased; }
    public void setPurchasedAt(Instant purchasedAt) { this.purchasedAt = purchasedAt; }
    public void setReason(String reason) { this.reason = reason; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public void setFeatures(List<String> features) { this.features = features; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public enum RecommendationType {
        PERSONALIZED, SIMILAR, TRENDING, SEASONAL, CROSS_SELL, UP_SELL
    }
}
