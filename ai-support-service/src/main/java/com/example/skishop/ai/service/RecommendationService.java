package com.example.skishop.ai.service;

import com.example.skishop.ai.client.ProductCandidateClient;
import com.example.skishop.ai.client.ProductCandidateClient.ProductCandidate;
import com.example.skishop.ai.client.SalesRecommendationClient;
import com.example.skishop.ai.dto.RecommendationResponse;
import com.example.skishop.ai.dto.SimilarProductResponse;
import com.example.skishop.ai.dto.TrendingProductResponse;
import com.example.skishop.ai.dto.RecommendationFeedbackRequest;
import com.example.skishop.ai.dto.FeedbackResponse;
import com.example.skishop.ai.model.ProductRecommendation;
import com.example.skishop.ai.model.Recommendation;
import com.example.skishop.ai.model.Recommendation.RecommendationType;
import com.example.skishop.ai.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String RECOMMENDATION_MODEL_TYPE = "RECOMMENDATION";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final RecommendationRepository recommendationRepository;
    private final ModelManagementService modelManagementService;
    private final ProductCandidateClient productCandidateClient;
    private final SalesRecommendationClient salesRecommendationClient;
    private final ChatClient chatClient;

    public RecommendationService(RecommendationRepository recommendationRepository,
                 ModelManagementService modelManagementService,
                 ProductCandidateClient productCandidateClient,
                 SalesRecommendationClient salesRecommendationClient,
                 ChatClient.Builder chatClientBuilder) {
        this.recommendationRepository = recommendationRepository;
    this.modelManagementService = modelManagementService;
    this.productCandidateClient = productCandidateClient;
    this.salesRecommendationClient = salesRecommendationClient;
        this.chatClient = chatClientBuilder.build();
    }

    public RecommendationResponse getPersonalizedRecommendations(String userId, int limit, String category) {
    String activeModel = modelManagementService.resolveActiveModelDescriptor(RECOMMENDATION_MODEL_TYPE);
    int safeLimit = normalizeLimit(limit);
    List<ProductCandidate> candidates = productCandidateClient.findCandidates(category, safeLimit * 3);
    List<ProductRecommendation> products = rankCandidates(candidates, category, Map.of(), safeLimit).stream()
        .map(ranked -> toProductRecommendation(ranked, activeModel, "personalized", category))
        .toList();

        Recommendation rec = new Recommendation(
        userId, RecommendationType.PERSONALIZED, products, averageScore(products),
        "deterministic-inventory-ranking@" + activeModel);
    rec.setReason("Personalized recommendations based on active inventory candidates");
        rec = recommendationRepository.save(rec);

        log.info("Generated {} recommendations for user {}", products.size(), userId);
        return toResponse(rec);
    }

    public Page<RecommendationResponse> getRecommendationHistory(String userId, Pageable pageable) {
        return recommendationRepository.findByUserId(userId, pageable)
                .map(this::toResponse);
    }

    public void recordClick(String recommendationId) {
        recommendationRepository.findById(Objects.requireNonNull(recommendationId)).ifPresent(rec -> {
            rec.setClicked(true);
            rec.setClickedAt(Instant.now());
            recommendationRepository.save(rec);
            log.info("Recorded click on recommendation {}", recommendationId);
        });
    }

    public SimilarProductResponse getSimilarProducts(String productId, int limit, String userId) {
        String activeModel = modelManagementService.resolveActiveModelDescriptor(RECOMMENDATION_MODEL_TYPE);
        int safeLimit = normalizeLimit(limit);
        var target = productCandidateClient.findByIdOrSku(productId);
        if (target.isEmpty() || !isRecommendable(target.get())) {
            return new SimilarProductResponse(productId, List.of(), 0);
        }

        ProductCandidate targetProduct = target.get();
        String userContext = userId == null || userId.isBlank() ? "anonymous" : userId;
        List<ProductCandidate> candidates = productCandidateClient
            .findCandidates(targetProduct.categoryId(), safeLimit * 4)
            .stream()
            .filter(candidate -> !Objects.equals(candidate.productId(), targetProduct.productId()))
            .toList();

        List<ProductRecommendation> similar = rankCandidates(candidates, targetProduct.categoryId(), Map.of(), safeLimit).stream()
            .map(ranked -> toProductRecommendation(ranked, activeModel, "similar",
                targetProduct.name() + " / user=" + userContext))
            .toList();

        log.info("Generated {} similar products for productId={}", similar.size(), productId);
        return new SimilarProductResponse(productId, similar, similar.size());
    }

    public TrendingProductResponse getTrendingProducts(String category, int limit, String timeframe) {
        String activeModel = modelManagementService.resolveActiveModelDescriptor(RECOMMENDATION_MODEL_TYPE);
        int safeLimit = normalizeLimit(limit);
        List<String> topProductIds = salesRecommendationClient.fetchTopProductIds(safeLimit * 2);
        Map<String, Integer> preferredRanks = toPreferredRanks(topProductIds);
        List<ProductCandidate> candidates = topProductIds.stream()
            .map(productCandidateClient::findByIdOrSku)
            .flatMap(Optional::stream)
            .filter(candidate -> category == null || category.isBlank()
                || category.equalsIgnoreCase(candidate.categoryId()))
            .toList();
        if (candidates.isEmpty()) {
            candidates = productCandidateClient.findCandidates(category, safeLimit * 3);
        }

        List<ProductRecommendation> trending = rankCandidates(candidates, category, preferredRanks, safeLimit).stream()
            .map(ranked -> toProductRecommendation(ranked, activeModel, "trending", timeframe))
            .toList();

        log.info("Generated trending products for category={}, timeframe={}", category, timeframe);
        return new TrendingProductResponse(
                category != null ? category : "all", timeframe != null ? timeframe : "weekly",
                trending, trending.size());
    }

    public FeedbackResponse recordFeedback(RecommendationFeedbackRequest request) {
        if (log.isInfoEnabled()) {
            log.info("Recorded recommendation feedback for id={}, helpful={}",
                    request.recommendationId(), request.helpful());
        }
        return new FeedbackResponse(
                UUID.randomUUID().toString(), "RECEIVED", "Feedback recorded successfully");
    }

    private RecommendationResponse toResponse(Recommendation rec) {
        return new RecommendationResponse(
                rec.getId(),
                rec.getUserId(),
                rec.getType(),
                rec.getProducts(),
                rec.getConfidenceScore(),
                rec.getAlgorithm(),
                rec.getCreatedAt()
        );
    }

    private List<RankedCandidate> rankCandidates(List<ProductCandidate> candidates, String category,
                                                 Map<String, Integer> preferredRanks, int limit) {
        List<RankedCandidate> sorted = candidates.stream()
                .filter(this::isRecommendable)
                .map(candidate -> new RankedCandidate(candidate, score(candidate, category, preferredRanks)))
                .sorted(Comparator.comparingDouble(RankedCandidate::score).reversed()
                        .thenComparing(ranked -> ranked.candidate().productId()))
                .limit(limit)
                .toList();
        return IntStream.range(0, sorted.size())
            .mapToObj(index -> new RankedCandidate(sorted.get(index).candidate(), sorted.get(index).score(), index + 1))
            .toList();
    }

    private boolean isRecommendable(ProductCandidate candidate) {
        return candidate != null
                && candidate.productId() != null
                && !candidate.productId().isBlank()
                && "ACTIVE".equalsIgnoreCase(candidate.status())
                && candidate.availableQuantity() > 0;
    }

    private double score(ProductCandidate candidate, String category, Map<String, Integer> preferredRanks) {
        double categoryScore = 0.5;
        if (category != null && !category.isBlank()) {
            categoryScore = category.equalsIgnoreCase(candidate.categoryId()) ? 1.0 : 0.0;
        }
        double stockScore = Math.min(1.0, candidate.availableQuantity() / 20.0);
        double priceScore = candidate.price() != null || candidate.salePrice() != null ? 1.0 : 0.0;
        double tagScore = candidate.tags().isEmpty() ? 0.0 : 1.0;
        double trendScore = preferredTrendScore(candidate, preferredRanks);
        double jitter = deterministicJitter(candidate.productId());

        return roundScore(categoryScore * 0.35
                + stockScore * 0.20
                + priceScore * 0.10
                + tagScore * 0.10
                + trendScore * 0.15
                + jitter * 0.10);
    }

    private double preferredTrendScore(ProductCandidate candidate, Map<String, Integer> preferredRanks) {
        Integer rank = preferredRanks.get(candidate.productId());
        if (rank == null && candidate.sku() != null) {
            rank = preferredRanks.get(candidate.sku());
        }
        return rank == null ? 0.2 : 1.0 / (rank + 1.0);
    }

    private double deterministicJitter(String productId) {
        int bucket = Math.floorMod(productId.hashCode(), 100);
        return bucket / 100.0;
    }

    private ProductRecommendation toProductRecommendation(RankedCandidate ranked, String activeModel,
                                                          String mode, String context) {
        ProductCandidate candidate = ranked.candidate();
        String reason = generateReason(candidate, activeModel, mode, context, fallbackReason(candidate, mode));
        ProductRecommendation recommendation = new ProductRecommendation(candidate.productId(), ranked.score(), reason, ranked.rank());
        recommendation.setFeatures(candidate.tags());
        recommendation.setAttributes(Map.of(
                "sku", candidate.sku() == null ? "" : candidate.sku(),
                "categoryId", candidate.categoryId() == null ? "" : candidate.categoryId(),
                "brand", candidate.brand() == null ? "" : candidate.brand(),
                "availableQuantity", candidate.availableQuantity(),
                "status", candidate.status() == null ? "" : candidate.status()));
        return recommendation;
    }

    private String generateReason(ProductCandidate candidate, String activeModel, String mode,
                                  String context, String fallback) {
        String promptText = """
                Generate one concise Japanese recommendation reason for this ski shop product.
                Mode: %s
                Active model: %s
                Context: %s
                Product: %s / %s / category=%s / available=%d
                Return only the reason text.
                """.formatted(mode, activeModel, context == null ? "none" : context,
                candidate.productId(), candidate.name(), candidate.categoryId(), candidate.availableQuantity());
        try {
            String content = chatClient.prompt(new Prompt(promptText)).call().content();
            if (content != null && !content.isBlank()) {
                return content.trim();
            }
        } catch (RuntimeException ex) {
            log.warn("AI reason generation failed for productId={}: {}", candidate.productId(), ex.getMessage());
        }
        return fallback;
    }

    private String fallbackReason(ProductCandidate candidate, String mode) {
        String name = candidate.name() == null || candidate.name().isBlank() ? candidate.productId() : candidate.name();
        String category = candidate.categoryId() == null || candidate.categoryId().isBlank() ? "all" : candidate.categoryId();
        return "%s recommendation: %s is active in category %s with %d units available."
                .formatted(mode, name, category, candidate.availableQuantity());
    }

    private double averageScore(List<ProductRecommendation> products) {
        return products.isEmpty()
                ? 0.0
                : products.stream().mapToDouble(ProductRecommendation::getScore).average().orElse(0.0);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Map<String, Integer> toPreferredRanks(List<String> productIds) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < productIds.size(); index++) {
            ranks.putIfAbsent(productIds.get(index), index);
        }
        return ranks;
    }

    private double roundScore(double score) {
        return Math.round(Math.max(0.0, Math.min(1.0, score)) * 100.0) / 100.0;
    }

    private record RankedCandidate(ProductCandidate candidate, double score, int rank) {
        private RankedCandidate(ProductCandidate candidate, double score) {
            this(candidate, score, 0);
        }
    }
}
