package com.example.skishop.ai.model;

import java.util.List;
import java.util.UUID;

public class SearchQuery {

    private String id;
    private String searchAnalyticsId;
    private String originalQuery;
    private String processedQuery;
    private List<String> keywords;
    private List<String> synonyms;
    private String intent;
    private double confidenceScore;

    public SearchQuery() {
        this.id = UUID.randomUUID().toString();
    }

    public SearchQuery(String searchAnalyticsId, String originalQuery, String processedQuery) {
        this();
        this.searchAnalyticsId = searchAnalyticsId;
        this.originalQuery = originalQuery;
        this.processedQuery = processedQuery;
    }

    public String getId() { return id; }
    public String getSearchAnalyticsId() { return searchAnalyticsId; }
    public String getOriginalQuery() { return originalQuery; }
    public String getProcessedQuery() { return processedQuery; }
    public List<String> getKeywords() { return keywords; }
    public List<String> getSynonyms() { return synonyms; }
    public String getIntent() { return intent; }
    public double getConfidenceScore() { return confidenceScore; }

    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public void setSynonyms(List<String> synonyms) { this.synonyms = synonyms; }
    public void setIntent(String intent) { this.intent = intent; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
}
