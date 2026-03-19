package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "behavior_metrics")
public class BehaviorMetrics {

    @Id
    private String id;
    private String userId;
    private String metricType;
    private double value;
    private Instant timestamp;
    private Map<String, Object> context;
    private String category;
    private java.util.List<String> tags;

    public BehaviorMetrics() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public BehaviorMetrics(String userId, String metricType, double value, String category) {
        this();
        this.userId = userId;
        this.metricType = metricType;
        this.value = value;
        this.category = category;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getMetricType() { return metricType; }
    public double getValue() { return value; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getContext() { return context; }
    public String getCategory() { return category; }
    public java.util.List<String> getTags() { return tags; }

    public void setContext(Map<String, Object> context) { this.context = context; }
    public void setTags(java.util.List<String> tags) { this.tags = tags; }
}
