package com.example.skishop.ai.controller;

import com.example.skishop.ai.dto.*;
import com.example.skishop.ai.service.SearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(searchService.search(query, category, page, size));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(searchService.autocomplete(query, limit));
    }

    @PostMapping("/semantic")
    public ResponseEntity<SearchResponse> semanticSearch(
            @Valid @RequestBody SemanticSearchRequest request) {
        return ResponseEntity.ok(searchService.semanticSearch(request));
    }

    @PostMapping("/feedback")
    public ResponseEntity<FeedbackResponse> searchFeedback(
            @Valid @RequestBody SearchFeedbackRequest request) {
        return ResponseEntity.ok(searchService.recordSearchFeedback(request));
    }

    @GetMapping("/analytics")
    public ResponseEntity<SearchAnalyticsResponse> getSearchAnalytics() {
        return ResponseEntity.ok(searchService.getSearchAnalytics());
    }
}
