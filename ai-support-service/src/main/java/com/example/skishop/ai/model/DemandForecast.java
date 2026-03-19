package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "demand_forecasts")
public class DemandForecast {

    @Id
    private String id;
    private String productId;
    private String category;
    private String period;
    private double predictedDemand;
    private Double actualDemand;
    private Double accuracy;
    private Instant forecastDate;
    private Instant periodStart;
    private Instant periodEnd;
    private String algorithm;
    private Map<String, Object> features;
    private double confidenceInterval;
    private double seasonalFactor;
    private double trendFactor;
    private Map<String, Object> externalFactors;

    public DemandForecast() {
        this.id = UUID.randomUUID().toString();
        this.forecastDate = Instant.now();
        this.seasonalFactor = 1.0;
        this.trendFactor = 1.0;
    }

    public DemandForecast(String productId, String category, String period,
                          double predictedDemand, String algorithm, double confidenceInterval,
                          Instant periodStart, Instant periodEnd) {
        this();
        this.productId = productId;
        this.category = category;
        this.period = period;
        this.predictedDemand = predictedDemand;
        this.algorithm = algorithm;
        this.confidenceInterval = confidenceInterval;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getCategory() { return category; }
    public String getPeriod() { return period; }
    public double getPredictedDemand() { return predictedDemand; }
    public Double getActualDemand() { return actualDemand; }
    public Double getAccuracy() { return accuracy; }
    public Instant getForecastDate() { return forecastDate; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public String getAlgorithm() { return algorithm; }
    public Map<String, Object> getFeatures() { return features; }
    public double getConfidenceInterval() { return confidenceInterval; }
    public double getSeasonalFactor() { return seasonalFactor; }
    public double getTrendFactor() { return trendFactor; }
    public Map<String, Object> getExternalFactors() { return externalFactors; }

    public void setActualDemand(Double actualDemand) { this.actualDemand = actualDemand; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
    public void setFeatures(Map<String, Object> features) { this.features = features; }
    public void setSeasonalFactor(double seasonalFactor) { this.seasonalFactor = seasonalFactor; }
    public void setTrendFactor(double trendFactor) { this.trendFactor = trendFactor; }
    public void setExternalFactors(Map<String, Object> externalFactors) { this.externalFactors = externalFactors; }
}
