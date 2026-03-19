package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.AutocompleteResponse;
import com.example.skishop.ai.dto.FeedbackResponse;
import com.example.skishop.ai.dto.SearchAnalyticsResponse;
import com.example.skishop.ai.dto.SearchFeedbackRequest;
import com.example.skishop.ai.dto.SearchResponse;
import com.example.skishop.ai.dto.SemanticSearchRequest;
import com.example.skishop.ai.repository.SearchAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchAnalyticsRepository searchAnalyticsRepository;
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
        searchService = new SearchService(searchAnalyticsRepository, chatClientBuilder);
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
        assertThat(response.results()).isNotEmpty();
        assertThat(response.totalResults()).isEqualTo(3);
        assertThat(response.responseTimeMs()).isGreaterThanOrEqualTo(0);
        verify(searchAnalyticsRepository).save(any());
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
        assertThat(response.results()).hasSize(3);
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
    }

    @Test
    @DisplayName("オートコンプリートが候補を返す")
    void should_returnSuggestions_when_autocomplete() {
        // Act
        AutocompleteResponse response = searchService.autocomplete("ski", 5);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.query()).isEqualTo("ski");
        assertThat(response.suggestions()).isNotEmpty();
    }

    @Test
    @DisplayName("検索フィードバックが記録される")
    void should_recordFeedback_when_searchFeedback() {
        // Arrange
        SearchFeedbackRequest request = new SearchFeedbackRequest("ski boots", "prod-001", true, "user-001");

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
}
