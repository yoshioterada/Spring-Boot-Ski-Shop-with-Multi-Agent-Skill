package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = "search_analytics")
public class SearchAnalytics {

    @Id
    private String id;
    private String userId;
    private String query;
    private String processedQuery;
    private String category;
    private int resultCount;
    private boolean hasResults;
    private Instant timestamp;
    private List<String> clickedResults;
    private double averageRelevanceScore;
    private String searchType;
    private Map<String, Object> filters;
    private long responseTime;
    private String location;
    private String device;
    private String sessionId;

    public SearchAnalytics() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public SearchAnalytics(String userId, String query, String processedQuery,
                           int resultCount, String searchType, long responseTime) {
        this();
        this.userId = userId;
        this.query = query;
        this.processedQuery = processedQuery;
        this.resultCount = resultCount;
        this.hasResults = resultCount > 0;
        this.searchType = searchType;
        this.responseTime = responseTime;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getQuery() { return query; }
    public String getProcessedQuery() { return processedQuery; }
    public String getCategory() { return category; }
    public int getResultCount() { return resultCount; }
    public boolean isHasResults() { return hasResults; }
    public Instant getTimestamp() { return timestamp; }
    public List<String> getClickedResults() { return clickedResults; }
    public double getAverageRelevanceScore() { return averageRelevanceScore; }
    public String getSearchType() { return searchType; }
    public Map<String, Object> getFilters() { return filters; }
    public long getResponseTime() { return responseTime; }

    public void setCategory(String category) { this.category = category; }
    public void setClickedResults(List<String> clickedResults) { this.clickedResults = clickedResults; }
    public void setAverageRelevanceScore(double averageRelevanceScore) { this.averageRelevanceScore = averageRelevanceScore; }
    public void setFilters(Map<String, Object> filters) { this.filters = filters; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
