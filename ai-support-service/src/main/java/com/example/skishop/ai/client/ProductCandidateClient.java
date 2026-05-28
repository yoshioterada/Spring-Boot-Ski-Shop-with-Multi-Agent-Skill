package com.example.skishop.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class ProductCandidateClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCandidateClient.class);
    private static final int DEFAULT_FETCH_SIZE = 50;

    private final WebClient inventoryWebClient;

    public ProductCandidateClient(@Qualifier("inventoryWebClient") WebClient inventoryWebClient) {
        this.inventoryWebClient = inventoryWebClient;
    }

    public List<ProductCandidate> findCandidates(String category, int limit) {
        int fetchSize = Math.max(limit, DEFAULT_FETCH_SIZE);
        ProductPage page = fetchPage(category, fetchSize);
        return page.content().stream().map(ProductCandidate::from).toList();
    }

    public Optional<ProductCandidate> findByIdOrSku(String productIdOrSku) {
        if (productIdOrSku == null || productIdOrSku.isBlank()) {
            return Optional.empty();
        }
        return fetchProductById(productIdOrSku.trim())
            .or(() -> fetchProductBySku(productIdOrSku.trim()))
                .map(ProductCandidate::from);
    }

    private ProductPage fetchPage(String category, int fetchSize) {
        try {
            if (category != null && !category.isBlank()) {
                return inventoryWebClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/products/category/{categoryId}")
                                .queryParam("page", 0)
                                .queryParam("size", fetchSize)
                                .build(category.trim()))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ProductPage>() {})
                        .blockOptional()
                        .orElseGet(() -> new ProductPage(List.of(), 0));
            }
            return inventoryWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/products")
                            .queryParam("page", 0)
                            .queryParam("size", fetchSize)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ProductPage>() {})
                    .blockOptional()
                    .orElseGet(() -> new ProductPage(List.of(), 0));
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch product candidates from inventory: {}", ex.getMessage());
            return new ProductPage(List.of(), 0);
        }
    }

    private Optional<ProductResponse> fetchProductById(String value) {
        try {
                return inventoryWebClient.get()
                    .uri("/api/v1/products/{id}", value)
                    .retrieve()
                    .bodyToMono(ProductResponse.class)
                    .blockOptional();
        } catch (RuntimeException ex) {
            log.debug("Failed to fetch inventory product by id={}: {}", value, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ProductResponse> fetchProductBySku(String value) {
        try {
            return inventoryWebClient.get()
                    .uri("/api/v1/products/sku/{sku}", value)
                    .retrieve()
                    .bodyToMono(ProductResponse.class)
                    .blockOptional();
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch inventory product by sku={}: {}", value, ex.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductPage(List<ProductResponse> content, long totalElements) {
        public ProductPage {
            content = content == null ? List.of() : content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductResponse(
            String id,
            String sku,
            String name,
            String description,
            String brand,
            String categoryId,
            BigDecimal regularPrice,
            BigDecimal salePrice,
            String currency,
            int stockQuantity,
            int availableQuantity,
            String status,
            List<String> tags
    ) {}

    public record ProductCandidate(
            String productId,
            String sku,
            String name,
            String description,
            String categoryId,
            String brand,
            BigDecimal price,
            BigDecimal salePrice,
            List<String> tags,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            String status
    ) {
        static ProductCandidate from(ProductResponse response) {
            int reservedQuantity = Math.max(0, response.stockQuantity() - response.availableQuantity());
            return new ProductCandidate(
                    response.id(),
                    response.sku(),
                    response.name(),
                    response.description(),
                    response.categoryId(),
                    response.brand(),
                    response.regularPrice(),
                    response.salePrice(),
                    response.tags() == null ? List.of() : response.tags(),
                    response.stockQuantity(),
                    reservedQuantity,
                    response.availableQuantity(),
                    response.status());
        }
    }
}