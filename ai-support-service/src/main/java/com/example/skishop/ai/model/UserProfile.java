package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "user_profiles")
public class UserProfile {

    @Id
    private String id;
    private Map<String, Object> preferences = new HashMap<>();
    private List<String> purchaseHistory = new ArrayList<>();
    private List<String> viewedProducts = new ArrayList<>();
    private List<String> searchHistory = new ArrayList<>();
    private Map<String, Double> categoryPreferences = new HashMap<>();
    private String loyaltyTier;
    private double totalSpent;
    @Indexed
    private Instant lastActivity;
    private List<String> favoriteCategories = new ArrayList<>();
    private Map<String, Object> behaviorMetrics = new HashMap<>();
    private Map<String, Object> seasonalPreferences = new HashMap<>();
    private Map<String, Object> devicePreferences = new HashMap<>();
    private Instant createdAt;
    private Instant updatedAt;

    public UserProfile() {
        this.loyaltyTier = "BRONZE";
        this.totalSpent = 0.0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.lastActivity = Instant.now();
    }

    public UserProfile(String id) {
        this();
        this.id = id;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }
    public List<String> getPurchaseHistory() { return purchaseHistory; }
    public void setPurchaseHistory(List<String> purchaseHistory) { this.purchaseHistory = purchaseHistory; }
    public List<String> getViewedProducts() { return viewedProducts; }
    public void setViewedProducts(List<String> viewedProducts) { this.viewedProducts = viewedProducts; }
    public List<String> getSearchHistory() { return searchHistory; }
    public void setSearchHistory(List<String> searchHistory) { this.searchHistory = searchHistory; }
    public Map<String, Double> getCategoryPreferences() { return categoryPreferences; }
    public void setCategoryPreferences(Map<String, Double> categoryPreferences) { this.categoryPreferences = categoryPreferences; }
    public String getLoyaltyTier() { return loyaltyTier; }
    public void setLoyaltyTier(String loyaltyTier) { this.loyaltyTier = loyaltyTier; }
    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }
    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
    public List<String> getFavoriteCategories() { return favoriteCategories; }
    public void setFavoriteCategories(List<String> favoriteCategories) { this.favoriteCategories = favoriteCategories; }
    public Map<String, Object> getBehaviorMetrics() { return behaviorMetrics; }
    public void setBehaviorMetrics(Map<String, Object> behaviorMetrics) { this.behaviorMetrics = behaviorMetrics; }
    public Map<String, Object> getSeasonalPreferences() { return seasonalPreferences; }
    public void setSeasonalPreferences(Map<String, Object> seasonalPreferences) { this.seasonalPreferences = seasonalPreferences; }
    public Map<String, Object> getDevicePreferences() { return devicePreferences; }
    public void setDevicePreferences(Map<String, Object> devicePreferences) { this.devicePreferences = devicePreferences; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
