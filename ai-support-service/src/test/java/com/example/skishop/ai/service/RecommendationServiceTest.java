package com.example.skishop.ai.service;

import com.example.skishop.ai.client.ProductCandidateClient;
import com.example.skishop.ai.client.ProductCandidateClient.ProductCandidate;
import com.example.skishop.ai.client.SalesRecommendationClient;
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

import java.math.BigDecimal;
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
    private ModelManagementService modelManagementService;
    @Mock
    private ProductCandidateClient productCandidateClient;
    @Mock
    private SalesRecommendationClient salesRecommendationClient;
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
        lenient().when(modelManagementService.resolveActiveModelDescriptor(anyString()))
            .thenReturn("RECOMMENDATION:v1.0");
        lenient().when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        lenient().when(callResponseSpec.content()).thenReturn("AI generated reason");
        lenient().when(salesRecommendationClient.fetchTopProductIds(anyInt())).thenReturn(List.of());
        recommendationService = new RecommendationService(
            recommendationRepository, modelManagementService, productCandidateClient,
            salesRecommendationClient, chatClientBuilder);
    }

    @Test
    @DisplayName("inventory 候補の商品IDだけでパーソナライズ推薦が生成される")
    void should_generateRecommendations_when_validRequest() {
        // Arrange
        when(productCandidateClient.findCandidates("ski", 30)).thenReturn(List.of(
                candidate("inv-001", "sku-001", "ski", 12, "ACTIVE"),
                candidate("inv-002", "sku-002", "ski", 7, "ACTIVE"),
                candidate("inv-003", "sku-003", "ski", 0, "ACTIVE"),
                candidate("inv-004", "sku-004", "ski", 9, "DISCONTINUED"),
                candidate("", "sku-005", "ski", 9, "ACTIVE")
        ));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations("user-001", 10, "ski");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("user-001");
        assertThat(response.algorithm()).contains("RECOMMENDATION:v1.0");
        assertThat(response.products())
            .extracting(ProductRecommendation::getProductId)
            .containsExactlyInAnyOrder("inv-001", "inv-002");
        verify(recommendationRepository).save(any(Recommendation.class));
    }

    @Test
    @DisplayName("カテゴリなしでもレコメンデーションが生成される")
    void should_generateRecommendations_when_noCategory() {
        // Arrange
        when(productCandidateClient.findCandidates(null, 15)).thenReturn(List.of(
            candidate("inv-001", "sku-001", "ski", 12, "ACTIVE"),
            candidate("inv-002", "sku-002", "snowboard", 7, "ACTIVE")
        ));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations("user-001", 5, null);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.products()).hasSize(2);
    }

    @Test
    @DisplayName("LLM 例外時も inventory 候補から deterministic fallback reason で推薦される")
    void should_returnInventoryCandidates_when_llmReasonGenerationFails() {
        // Arrange
        when(productCandidateClient.findCandidates("ski", 6)).thenReturn(List.of(
                candidate("inv-001", "sku-001", "ski", 12, "ACTIVE")
        ));
        when(chatClient.prompt(any(Prompt.class))).thenThrow(new RuntimeException("AI unavailable"));
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations("user-001", 2, "ski");

        // Assert
        assertThat(response.products()).hasSize(1);
        assertThat(response.products().getFirst().getProductId()).isEqualTo("inv-001");
        assertThat(response.products().getFirst().getReason()).contains("personalized recommendation");
    }

    @Test
    @DisplayName("LLM が空文字を返した場合も deterministic fallback reason になる")
    void should_useFallbackReason_when_llmReturnsBlankReason() {
        // Arrange
        when(productCandidateClient.findCandidates("ski", 6)).thenReturn(List.of(
                candidate("inv-001", "sku-001", "ski", 12, "ACTIVE")
        ));
        when(callResponseSpec.content()).thenReturn("   ");
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations("user-001", 2, "ski");

        // Assert
        assertThat(response.products()).hasSize(1);
        assertThat(response.products().getFirst().getProductId()).isEqualTo("inv-001");
        assertThat(response.products().getFirst().getReason()).contains("personalized recommendation");
    }

    @Test
    @DisplayName("候補が空の場合は固定 ID fallback を返さない")
    void should_returnEmptyRecommendation_when_noCandidates() {
        // Arrange
        when(productCandidateClient.findCandidates("ski", 6)).thenReturn(List.of());
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations("user-001", 2, "ski");

        // Assert
        assertThat(response.products()).isEmpty();
        verify(chatClient, never()).prompt(any(Prompt.class));
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
        @DisplayName("類似商品一覧は対象商品自身を除外して active available 商品だけ返す")
    void should_returnSimilarProducts_when_validProductId() {
        // Arrange
        ProductCandidate origin = candidate("origin-001", "sku-origin", "ski", 10, "ACTIVE");
        when(productCandidateClient.findByIdOrSku("origin-001")).thenReturn(Optional.of(origin));
        when(productCandidateClient.findCandidates("ski", 20)).thenReturn(List.of(
            origin,
            candidate("similar-101", "sku-101", "ski", 8, "ACTIVE"),
            candidate("similar-102", "sku-102", "ski", 0, "ACTIVE"),
            candidate("similar-103", "sku-103", "ski", 8, "INACTIVE")
        ));

        // Act
        SimilarProductResponse response = recommendationService.getSimilarProducts("origin-001", 5, "user-001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo("origin-001");
        assertThat(response.similarProducts())
            .extracting(ProductRecommendation::getProductId)
            .containsExactly("similar-101");
    }

    @Test
        @DisplayName("トレンド商品一覧は sales top products を inventory で検証して返す")
    void should_returnTrendingProducts_when_requested() {
        // Arrange
        when(salesRecommendationClient.fetchTopProductIds(20)).thenReturn(List.of("sku-101", "missing", "sku-102"));
        when(productCandidateClient.findByIdOrSku("sku-101"))
            .thenReturn(Optional.of(candidate("inv-101", "sku-101", "ski", 8, "ACTIVE")));
        when(productCandidateClient.findByIdOrSku("missing")).thenReturn(Optional.empty());
        when(productCandidateClient.findByIdOrSku("sku-102"))
            .thenReturn(Optional.of(candidate("inv-102", "sku-102", "ski", 0, "ACTIVE")));

        // Act
        TrendingProductResponse response = recommendationService.getTrendingProducts("ski", 10, "weekly");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.category()).isEqualTo("ski");
        assertThat(response.trendingProducts())
            .extracting(ProductRecommendation::getProductId)
            .containsExactly("inv-101");
        }

        @Test
        @DisplayName("sales top products がない場合は inventory active available 候補で trending fallback する")
        void should_returnTrendingProductsFromInventoryFallback_when_salesUnavailable() {
        // Arrange
        when(salesRecommendationClient.fetchTopProductIds(6)).thenReturn(List.of());
        when(productCandidateClient.findCandidates("ski", 9)).thenReturn(List.of(
            candidate("inv-201", "sku-201", "ski", 5, "ACTIVE"),
            candidate("inv-202", "sku-202", "ski", 0, "ACTIVE")
        ));

        // Act
        TrendingProductResponse response = recommendationService.getTrendingProducts("ski", 3, "weekly");

        // Assert
        assertThat(response.trendingProducts())
            .extracting(ProductRecommendation::getProductId)
            .containsExactly("inv-201");
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

    private ProductCandidate candidate(String productId, String sku, String category, int availableQuantity, String status) {
        return new ProductCandidate(
                productId,
                sku,
                "Test Product " + productId,
                "Description",
                category,
                "TestBrand",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(900),
                List.of("powder", "all-mountain"),
                availableQuantity + 2,
                2,
                availableQuantity,
                status);
    }
}
