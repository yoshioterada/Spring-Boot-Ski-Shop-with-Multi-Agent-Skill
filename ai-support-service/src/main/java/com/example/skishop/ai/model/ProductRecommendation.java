package com.example.skishop.ai.model;

import java.util.List;
import java.util.Map;

public class ProductRecommendation {

    private String productId;
    private double score;
    private String reason;
    private List<String> features;
    private Map<String, Object> attributes;
    private int rank;

    public ProductRecommendation() {}

    public ProductRecommendation(String productId, double score, String reason, int rank) {
        this.productId = productId;
        this.score = score;
        this.reason = reason;
        this.rank = rank;
    }

    public String getProductId() { return productId; }
    public double getScore() { return score; }
    public String getReason() { return reason; }
    public List<String> getFeatures() { return features; }
    public Map<String, Object> getAttributes() { return attributes; }
    public int getRank() { return rank; }

    public void setFeatures(List<String> features) { this.features = features; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}
