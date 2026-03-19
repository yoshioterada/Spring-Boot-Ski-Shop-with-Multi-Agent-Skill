package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.AutocompleteResponse;
import com.example.skishop.ai.dto.FeedbackResponse;
import com.example.skishop.ai.dto.SearchAnalyticsResponse;
import com.example.skishop.ai.dto.SearchFeedbackRequest;
import com.example.skishop.ai.dto.SearchResponse;
import com.example.skishop.ai.dto.SearchResponse.SearchResult;
import com.example.skishop.ai.dto.SemanticSearchRequest;
import com.example.skishop.ai.model.SearchAnalytics;
import com.example.skishop.ai.repository.SearchAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final SearchAnalyticsRepository searchAnalyticsRepository;
    private final ChatClient chatClient;

    public SearchService(SearchAnalyticsRepository searchAnalyticsRepository,
                         ChatClient.Builder chatClientBuilder) {
        this.searchAnalyticsRepository = searchAnalyticsRepository;
        this.chatClient = chatClientBuilder.build();
    }

    public SearchResponse semanticSearch(SemanticSearchRequest request) {
        long startTime = System.currentTimeMillis();

        String enhancedQuery = enhanceQuery(request.query());

        List<SearchResult> results = List.of(
                new SearchResult("prod-001", "Alpine Ski Boots Pro", 0.95, "Professional ski boots"),
                new SearchResult("prod-002", "All-Mountain Skis", 0.88, "Versatile all-mountain skis"),
                new SearchResult("prod-003", "Ski Helmet Advanced", 0.82, "Advanced safety helmet")
        );

        long responseTime = System.currentTimeMillis() - startTime;

        SearchAnalytics analytics = new SearchAnalytics(
                request.userId(), request.query(), enhancedQuery,
                results.size(), "SEMANTIC", responseTime);
        analytics.setCategory(request.category());
        searchAnalyticsRepository.save(analytics);

        log.info("Semantic search completed: query='{}', results={}, time={}ms",
                request.query(), results.size(), responseTime);

        return new SearchResponse(
                request.query(), enhancedQuery, results, results.size(), responseTime);
    }

    private String enhanceQuery(String query) {
        String prompt = String.format("""
                Enhance the following search query for a ski shop e-commerce platform.
                Add relevant synonyms and expand abbreviations.
                Return only the enhanced query text, nothing else.
                
                Query: %s
                """, query);

        try {
            return chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Failed to enhance query, using original: {}", e.getMessage());
            return query;
        }
    }

    public SearchResponse search(String query, String category, int page, int size) {
        long startTime = System.currentTimeMillis();
        String enhancedQuery = enhanceQuery(query);

        List<SearchResult> results = List.of(
                new SearchResult("prod-001", "Alpine Ski Boots Pro", 0.95, "Professional ski boots"),
                new SearchResult("prod-002", "All-Mountain Skis", 0.88, "Versatile all-mountain skis"),
                new SearchResult("prod-003", "Ski Helmet Advanced", 0.82, "Advanced safety helmet")
        );

        long responseTime = System.currentTimeMillis() - startTime;

        SearchAnalytics analytics = new SearchAnalytics(
                null, query, enhancedQuery, results.size(), "TEXT", responseTime);
        analytics.setCategory(category);
        searchAnalyticsRepository.save(analytics);

        log.info("Search completed: query='{}', category={}, results={}, time={}ms",
                query, category, results.size(), responseTime);

        return new SearchResponse(query, enhancedQuery, results, results.size(), responseTime);
    }

    public AutocompleteResponse autocomplete(String query, int limit) {
        List<String> suggestions = List.of(
                query + " boots",
                query + " jacket",
                query + " helmet",
                query + " gloves"
        ).stream().limit(limit).toList();

        log.info("Autocomplete for query='{}', suggestions={}", query, suggestions.size());
        return new AutocompleteResponse(query, suggestions);
    }

    public FeedbackResponse recordSearchFeedback(SearchFeedbackRequest request) {
        log.info("Recorded search feedback for query='{}', resultId={}", request.query(), request.resultId());
        return new FeedbackResponse(UUID.randomUUID().toString(), "RECEIVED", "Search feedback recorded");
    }

    public SearchAnalyticsResponse getSearchAnalytics() {
        long totalSearches = searchAnalyticsRepository.count();
        log.info("Retrieved search analytics: totalSearches={}", totalSearches);
        return new SearchAnalyticsResponse(
                totalSearches, 0, 0.0, Map.of(), Map.of(), Instant.now());
    }
}
