package com.example.skishop.ai.service;

import com.example.skishop.ai.client.InventorySearchClient;
import com.example.skishop.ai.client.InventorySearchClient.InventoryProduct;
import com.example.skishop.ai.client.InventorySearchClient.InventorySearchPage;
import com.example.skishop.ai.dto.AutocompleteResponse;
import com.example.skishop.ai.dto.FeedbackResponse;
import com.example.skishop.ai.dto.SearchAnalyticsResponse;
import com.example.skishop.ai.dto.SearchFeedbackRequest;
import com.example.skishop.ai.dto.SearchResponse;
import com.example.skishop.ai.dto.SemanticSearchRequest;
import com.example.skishop.ai.model.SearchAnalytics;
import com.example.skishop.ai.repository.SearchAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchAnalyticsRepository searchAnalyticsRepository;
    @Mock
    private ModelManagementService modelManagementService;
    @Mock
    private InventorySearchClient inventorySearchClient;
    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClientRequestSpec requestSpec;
    @Mock
    private CallResponseSpec callResponseSpec;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        lenient().when(modelManagementService.resolveActiveModelDescriptor(anyString()))
                .thenReturn("SEARCH:v1.0");
        lenient().when(inventorySearchClient.search(anyString(), anyString(), anyString(), any(), anyInt(), anyInt()))
            .thenReturn(new InventorySearchPage(List.of(activeProduct("real-001", "Alpine Ski Boots")), 1));
        searchService = new SearchService(searchAnalyticsRepository, modelManagementService, inventorySearchClient, chatClientBuilder);
    }

    @Test
    @DisplayName("セマンティック検索が正常に実行される")
    void should_returnSearchResults_when_validQuery() {
        // Arrange
        var request = new SemanticSearchRequest("ski boots", "footwear", "user-001", 10);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("enhanced ski boots query");
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.query()).isEqualTo("ski boots");
        assertThat(response.results()).extracting(SearchResponse.SearchResult::productId)
            .containsExactly("real-001");
        assertThat(response.totalResults()).isEqualTo(1);
        assertThat(response.responseTimeMs()).isGreaterThanOrEqualTo(0);
        verify(inventorySearchClient).search("ski boots", "enhanced ski boots query",
            "ai-support-semantic", "footwear", 0, 10);
        ArgumentCaptor<SearchAnalytics> analyticsCaptor = ArgumentCaptor.forClass(SearchAnalytics.class);
        verify(searchAnalyticsRepository).save(analyticsCaptor.capture());
        assertThat(analyticsCaptor.getValue().getSearchType()).isEqualTo("SEMANTIC:SEARCH:v1.0");
    }

    @Test
    @DisplayName("AI強化に失敗した場合、元のクエリが使用される")
    void should_useOriginalQuery_when_aiEnhancementFails() {
        // Arrange
        var request = new SemanticSearchRequest("ski boots", null, "user-001", 10);
        when(chatClient.prompt(any(Prompt.class))).thenThrow(new RuntimeException("AI error"));
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.query()).isEqualTo("ski boots");
        assertThat(response.processedQuery()).isEqualTo("ski boots");
        assertThat(response.results()).hasSize(1);
        verify(inventorySearchClient).search("ski boots", "ski boots", "ai-support-semantic", null, 0, 10);
        ArgumentCaptor<SearchAnalytics> analyticsCaptor = ArgumentCaptor.forClass(SearchAnalytics.class);
        verify(searchAnalyticsRepository).save(analyticsCaptor.capture());
        assertThat(analyticsCaptor.getValue().getFilters())
            .containsEntry("llmFallback", true)
            .containsEntry("inventoryAvailable", true);
        }

        @Test
        @DisplayName("AI強化が空文字を返した場合、元のクエリが使用される")
        void should_useOriginalQuery_when_aiEnhancementReturnsBlank() {
        // Arrange
        var request = new SemanticSearchRequest("ski boots", null, "user-001", 10);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("   ");
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response.processedQuery()).isEqualTo("ski boots");
        verify(inventorySearchClient).search("ski boots", "ski boots", "ai-support-semantic", null, 0, 10);
        ArgumentCaptor<SearchAnalytics> analyticsCaptor = ArgumentCaptor.forClass(SearchAnalytics.class);
        verify(searchAnalyticsRepository).save(analyticsCaptor.capture());
        assertThat(analyticsCaptor.getValue().getFilters())
            .containsEntry("llmFallback", true)
            .containsEntry("inventoryAvailable", true);
    }

    @Test
    @DisplayName("検索結果に応答時間が記録される")
    void should_recordResponseTime_when_searchCompleted() {
        // Arrange
        var request = new SemanticSearchRequest("helmets", "safety", "user-002", 5);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("enhanced helmets query");
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response.responseTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("基本検索が正常に実行される")
    void should_returnResults_when_basicSearch() {
        // Arrange
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("enhanced query");
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.search("ski boots", "footwear", 0, 20);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.query()).isEqualTo("ski boots");
        assertThat(response.results()).isNotEmpty();
        verify(inventorySearchClient).search("ski boots", "enhanced query", "ai-support-text", "footwear", 0, 20);
        }

        @Test
        @DisplayName("inventoryが2件返した場合、SearchResponseも2件返す")
        void should_returnTwoResults_when_inventoryReturnsTwoProducts() {
        // Arrange
        var request = new SemanticSearchRequest("ski boots", null, "user-001", 10);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("enhanced ski boots query");
        when(inventorySearchClient.search(anyString(), anyString(), eq("ai-support-semantic"), any(), eq(0), eq(10)))
            .thenReturn(new InventorySearchPage(List.of(
                activeProduct("real-001", "Alpine Ski Boots"),
                activeProduct("real-002", "All Mountain Skis")), 2));
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response.totalResults()).isEqualTo(2);
        assertThat(response.results()).extracting(SearchResponse.SearchResult::productId)
            .containsExactly("real-001", "real-002");
        }

        @Test
        @DisplayName("inventoryが0件返した場合、totalResultsは0になる")
        void should_returnZeroResults_when_inventoryReturnsNoProducts() {
        // Arrange
        var request = new SemanticSearchRequest("no hit", null, "user-001", 10);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("enhanced no hit");
        when(inventorySearchClient.search(anyString(), anyString(), eq("ai-support-semantic"), any(), eq(0), eq(10)))
            .thenReturn(new InventorySearchPage(List.of(), 0));
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response.totalResults()).isZero();
        assertThat(response.results()).isEmpty();
        ArgumentCaptor<SearchAnalytics> analyticsCaptor = ArgumentCaptor.forClass(SearchAnalytics.class);
        verify(searchAnalyticsRepository).save(analyticsCaptor.capture());
        assertThat(analyticsCaptor.getValue().getFilters())
            .containsEntry("llmFallback", false)
            .containsEntry("inventoryAvailable", true);
        }

        @Test
        @DisplayName("inventory障害時は制御された空結果を返す")
        void should_returnEmptyFallback_when_inventoryFails() {
        // Arrange
        var request = new SemanticSearchRequest("ski boots", null, "user-001", 10);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("enhanced ski boots query");
        when(inventorySearchClient.search(anyString(), anyString(), eq("ai-support-semantic"), any(), eq(0), eq(10)))
            .thenThrow(new IllegalStateException("inventory unavailable"));
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response.totalResults()).isZero();
        assertThat(response.results()).isEmpty();
        ArgumentCaptor<SearchAnalytics> analyticsCaptor = ArgumentCaptor.forClass(SearchAnalytics.class);
        verify(searchAnalyticsRepository).save(analyticsCaptor.capture());
        assertThat(analyticsCaptor.getValue().getFilters())
            .containsEntry("llmFallback", false)
            .containsEntry("inventoryAvailable", false);
        }

        @Test
        @DisplayName("INACTIVEや在庫なしの商品は検索結果から除外される")
        void should_filterUnavailableProducts_when_inventoryReturnsMixedStatuses() {
        // Arrange
        var request = new SemanticSearchRequest("ski", null, "user-001", 10);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("enhanced ski");
        when(inventorySearchClient.search(anyString(), anyString(), eq("ai-support-semantic"), any(), eq(0), eq(10)))
            .thenReturn(new InventorySearchPage(List.of(
                activeProduct("real-001", "Available Ski"),
                new InventoryProduct("inactive-001", "SKU-X", "Inactive", "", "Brand", "cat", 10, 10, "INACTIVE"),
                new InventoryProduct("empty-001", "SKU-Y", "Empty", "", "Brand", "cat", 10, 0, "ACTIVE")), 3));
        when(searchAnalyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SearchResponse response = searchService.semanticSearch(request);

        // Assert
        assertThat(response.results()).extracting(SearchResponse.SearchResult::productId)
            .containsExactly("real-001");
    }

    @Test
    @DisplayName("オートコンプリートが候補を返す")
    void should_returnSuggestions_when_autocomplete() {
        // Act
        AutocompleteResponse response = searchService.autocomplete("ski", 5);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.query()).isEqualTo("ski");
        assertThat(response.suggestions()).containsExactly("Alpine Ski Boots");
    }

    @Test
    @DisplayName("検索フィードバックが記録される")
    void should_recordFeedback_when_searchFeedback() {
        // Arrange
        SearchFeedbackRequest request = new SearchFeedbackRequest("ski boots", "result-001", true, "user-001");

        // Act
        FeedbackResponse response = searchService.recordSearchFeedback(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("検索アナリティクスが取得できる")
    void should_returnAnalytics_when_requested() {
        // Arrange
        when(searchAnalyticsRepository.count()).thenReturn(100L);

        // Act
        SearchAnalyticsResponse response = searchService.getSearchAnalytics();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.totalSearches()).isEqualTo(100);
    }

    private InventoryProduct activeProduct(String id, String name) {
        return new InventoryProduct(id, "SKU-" + id, name, "Product description", "Brand", "footwear", 20, 10, "ACTIVE");
    }
}
