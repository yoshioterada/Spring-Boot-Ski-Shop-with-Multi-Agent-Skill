package com.example.skishop.ai.service;

import com.example.skishop.ai.client.InventorySearchClient;
import com.example.skishop.ai.client.InventorySearchClient.InventoryProduct;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
        private static final String SEARCH_MODEL_TYPE = "SEARCH";
        private static final String SEMANTIC_SOURCE = "ai-support-semantic";
        private static final String TEXT_SOURCE = "ai-support-text";
        private static final int DEFAULT_AUTOCOMPLETE_LIMIT = 10;

    private final SearchAnalyticsRepository searchAnalyticsRepository;
        private final ModelManagementService modelManagementService;
        private final InventorySearchClient inventorySearchClient;
    private final ChatClient chatClient;

    public SearchService(SearchAnalyticsRepository searchAnalyticsRepository,
                                                 ModelManagementService modelManagementService,
                                                 InventorySearchClient inventorySearchClient,
                                                 ChatClient.Builder chatClientBuilder) {
        this.searchAnalyticsRepository = searchAnalyticsRepository;
                this.modelManagementService = modelManagementService;
                this.inventorySearchClient = inventorySearchClient;
        this.chatClient = chatClientBuilder.build();
    }

    public SearchResponse semanticSearch(SemanticSearchRequest request) {
        long startTime = System.currentTimeMillis();
        String activeModel = modelManagementService.resolveActiveModelDescriptor(SEARCH_MODEL_TYPE);

        QueryEnhancement enhancement = enhanceQuery(request.query(), activeModel);
        InventorySearchOutcome searchOutcome = searchInventory(
            request.query(), enhancement.processedQuery(), SEMANTIC_SOURCE, request.category(), 0, request.limit());
        List<SearchResult> results = searchOutcome.results();

        long responseTime = System.currentTimeMillis() - startTime;

        SearchAnalytics analytics = new SearchAnalytics(
            request.userId(), request.query(), enhancement.processedQuery(),
                results.size(), "SEMANTIC:" + activeModel, responseTime);
        analytics.setCategory(request.category());
        analytics.setFilters(fallbackFilters(enhancement, searchOutcome));
        searchAnalyticsRepository.save(analytics);

        if (log.isInfoEnabled()) {
            log.info("Semantic search completed: query='{}', results={}, time={}ms",
                    request.query(), results.size(), responseTime);
        }

        return new SearchResponse(
                request.query(), enhancement.processedQuery(), results, results.size(), responseTime);
    }

    private QueryEnhancement enhanceQuery(String query, String activeModel) {
        String prompt = String.format("""
                Enhance the following search query for a ski shop e-commerce platform.
                Use active search model: %s.
                Add relevant synonyms and expand abbreviations.
                Return only the enhanced query text, nothing else.
                
                Query: %s
                """, activeModel, query);

        try {
            String content = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();
            if (content != null && !content.isBlank()) {
                return new QueryEnhancement(content.trim(), false);
            }
            log.warn("AI query enhancement returned blank content, using original query");
        } catch (RuntimeException ex) {
            log.warn("Failed to enhance query, using original: {}", ex.getMessage());
        }
        return new QueryEnhancement(query, true);
    }

    public SearchResponse search(String query, String category, int page, int size) {
        long startTime = System.currentTimeMillis();
        String activeModel = modelManagementService.resolveActiveModelDescriptor(SEARCH_MODEL_TYPE);
        QueryEnhancement enhancement = enhanceQuery(query, activeModel);
        InventorySearchOutcome searchOutcome = searchInventory(query, enhancement.processedQuery(), TEXT_SOURCE, category, page, size);
        List<SearchResult> results = searchOutcome.results();

        long responseTime = System.currentTimeMillis() - startTime;

        SearchAnalytics analytics = new SearchAnalytics(
                null, query, enhancement.processedQuery(), results.size(), "TEXT:" + activeModel, responseTime);
        analytics.setCategory(category);
        analytics.setFilters(fallbackFilters(enhancement, searchOutcome));
        searchAnalyticsRepository.save(analytics);

        log.info("Search completed: query='{}', category={}, results={}, time={}ms",
                query, category, results.size(), responseTime);

        return new SearchResponse(query, enhancement.processedQuery(), results, results.size(), responseTime);
    }

    public AutocompleteResponse autocomplete(String query, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_AUTOCOMPLETE_LIMIT : limit;
        List<String> suggestions = searchInventory(query, query, "ai-support-autocomplete", null, 0, safeLimit).results()
                .stream()
                .map(SearchResult::name)
                .distinct()
                .limit(safeLimit)
                .toList();

        log.info("Autocomplete for query='{}', suggestions={}", query, suggestions.size());
        return new AutocompleteResponse(query, suggestions);
    }

    public FeedbackResponse recordSearchFeedback(SearchFeedbackRequest request) {
                if (log.isInfoEnabled()) {
                        log.info("Recorded search feedback for query='{}', resultId={}", request.query(), request.resultId());
                }
        return new FeedbackResponse(UUID.randomUUID().toString(), "RECEIVED", "Search feedback recorded");
    }

    public SearchAnalyticsResponse getSearchAnalytics() {
        long totalSearches = searchAnalyticsRepository.count();
        log.info("Retrieved search analytics: totalSearches={}", totalSearches);
        return new SearchAnalyticsResponse(
                totalSearches, 0, 0.0, Map.of(), Map.of(), Instant.now());
    }

    private InventorySearchOutcome searchInventory(String query, String enhancedQuery, String source,
                                                   String category, int page, int size) {
        try {
            var inventoryPage = inventorySearchClient.search(query, enhancedQuery, source, category, page, size);
            List<SearchResult> results = new ArrayList<>();
            int index = 0;
            for (InventoryProduct product : inventoryPage.content()) {
                if (!isSearchable(product)) {
                    continue;
                }
                results.add(toSearchResult(product, index));
                index++;
            }
            return new InventorySearchOutcome(results, true);
        } catch (RuntimeException ex) {
            log.warn("Inventory search failed for query='{}', returning empty controlled fallback: {}",
                    query, ex.getMessage());
            return new InventorySearchOutcome(List.of(), false);
        }
    }

    private Map<String, Object> fallbackFilters(QueryEnhancement enhancement, InventorySearchOutcome searchOutcome) {
        return Map.of(
                "llmFallback", enhancement.llmFallback(),
                "inventoryAvailable", searchOutcome.inventoryAvailable());
    }

    private boolean isSearchable(InventoryProduct product) {
        return product != null
                && product.id() != null
                && !product.id().isBlank()
                && "ACTIVE".equalsIgnoreCase(product.status())
                && product.availableQuantity() > 0;
    }

    private SearchResult toSearchResult(InventoryProduct product, int index) {
        double relevanceScore = Math.max(0.1, 1.0 - (index * 0.05));
        return new SearchResult(product.id(), safeName(product), relevanceScore, buildSnippet(product));
    }

    private String safeName(InventoryProduct product) {
        if (product.name() != null && !product.name().isBlank()) {
            return product.name();
        }
        return product.sku() != null ? product.sku() : product.id();
    }

    private String buildSnippet(InventoryProduct product) {
        if (product.description() != null && !product.description().isBlank()) {
            return product.description();
        }
        return "SKU %s / category %s / available %d".formatted(
                product.sku() == null ? product.id() : product.sku(),
                product.categoryId() == null ? "unknown" : product.categoryId(),
                product.availableQuantity());
    }

    private record QueryEnhancement(String processedQuery, boolean llmFallback) {}

    private record InventorySearchOutcome(List<SearchResult> results, boolean inventoryAvailable) {}
}
