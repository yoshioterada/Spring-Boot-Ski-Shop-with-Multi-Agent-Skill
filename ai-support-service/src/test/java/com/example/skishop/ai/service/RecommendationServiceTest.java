package com.example.skishop.ai.service;

import com.example.skishop.ai.dto.FeedbackResponse;
import com.example.skishop.ai.dto.RecommendationFeedbackRequest;
import com.example.skishop.ai.dto.RecommendationResponse;
import com.example.skishop.ai.dto.SimilarProductResponse;
import com.example.skishop.ai.dto.TrendingProductResponse;
import com.example.skishop.ai.model.ProductRecommendation;
import com.example.skishop.ai.model.Recommendation;
import com.example.skishop.ai.model.Recommendation.RecommendationType;
import com.example.skishop.ai.repository.RecommendationRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendationRepository recommendationRepository;
    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClientRequestSpec requestSpec;
    @Mock
    private CallResponseSpec callResponseSpec;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        recommendationService = new RecommendationService(recommendationRepository, chatClientBuilder);
    }

    @Test
    @DisplayName("パーソナライズされたレコメンデーションが生成される")
    void should_generateRecommendations_when_validRequest() {
        // Arrange
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("recommendations");
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations("user-001", 10, "ski");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("user-001");
        assertThat(response.products()).isNotEmpty();
        verify(recommendationRepository).save(any(Recommendation.class));
    }

    @Test
    @DisplayName("カテゴリなしでもレコメンデーションが生成される")
    void should_generateRecommendations_when_noCategory() {
        // Arrange
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("recommendations");
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations("user-001", 5, null);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.products()).hasSize(3);
    }

    @Test
    @DisplayName("レコメンデーション履歴をページネーション付きで取得できる")
    void should_returnHistory_when_historyExists() {
        // Arrange
        String userId = "user-001";
        Recommendation rec = new Recommendation(userId, RecommendationType.PERSONALIZED,
                List.of(new ProductRecommendation("prod-001", 0.9, "reason", 1)),
                0.89, "spring-ai-collaborative");
        Page<Recommendation> page = new PageImpl<>(List.of(rec));
        when(recommendationRepository.findByUserId(eq(userId), any())).thenReturn(page);

        // Act
        Page<RecommendationResponse> result = recommendationService.getRecommendationHistory(userId, PageRequest.of(0, 20));

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("レコメンデーションクリックを記録できる")
    void should_recordClick_when_recommendationExists() {
        // Arrange
        String recId = "rec-001";
        Recommendation rec = new Recommendation("user-001", RecommendationType.PERSONALIZED,
                List.of(), 0.89, "spring-ai");
        when(recommendationRepository.findById(recId)).thenReturn(Optional.of(rec));
        when(recommendationRepository.save(any(Recommendation.class))).thenReturn(rec);

        // Act
        recommendationService.recordClick(recId);

        // Assert
        verify(recommendationRepository).save(any(Recommendation.class));
    }

    @Test
    @DisplayName("存在しないレコメンデーションIDでクリック記録してもエラーにならない")
    void should_doNothing_when_recommendationNotFound() {
        // Arrange
        when(recommendationRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act
        recommendationService.recordClick("nonexistent");

        // Assert
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    @DisplayName("類似商品一覧が生成される")
    void should_returnSimilarProducts_when_validProductId() {
        // Arrange
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("similar products");

        // Act
        SimilarProductResponse response = recommendationService.getSimilarProducts("prod-001", 5, "user-001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo("prod-001");
        assertThat(response.similarProducts()).isNotEmpty();
    }

    @Test
    @DisplayName("トレンド商品一覧が生成される")
    void should_returnTrendingProducts_when_requested() {
        // Act
        TrendingProductResponse response = recommendationService.getTrendingProducts("ski", 10, "weekly");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.category()).isEqualTo("ski");
        assertThat(response.trendingProducts()).isNotEmpty();
    }

    @Test
    @DisplayName("レコメンデーションフィードバックが記録される")
    void should_recordFeedback_when_validRequest() {
        // Arrange
        RecommendationFeedbackRequest request = new RecommendationFeedbackRequest("rec-001", "user-001", true, "Helpful!");

        // Act
        FeedbackResponse response = recommendationService.recordFeedback(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("RECEIVED");
    }
}
