package com.example.skishop.sales.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * inventory-management-service との通信クライアント。
 * 売上分析でカテゴリ別売上を算出する際に productId → categoryId/categoryName を解決する。
 */
@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient restClient;

    public InventoryClient(@Value("${services.inventory.base-url:http://inventory-management-service:8082}") String baseUrl,
                           @Value("${internal.api-key}") String internalApiKey) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .defaultHeader("X-Caller-Service", "sales-management-service")
                .build();
    }

    /**
     * 指定された SKU のリストに対し、商品情報を取得する。
     * sales-management の order_items.product_id は SKU を保持しているため SKU で検索する。
     * inventory-management-service の POST /api/v1/products/batch-by-sku を呼び出す。
     * 失敗時は空 Map を返却し、呼び出し元はカテゴリ未解決として扱う。
     */
    public Map<String, ProductSummary> fetchProducts(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Map.of();
        }
        try {
            ProductBatchResponse[] responses = restClient.post()
                    .uri("/api/v1/products/batch-by-sku")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(skus)
                    .retrieve()
                    .body(ProductBatchResponse[].class);
            if (responses == null) {
                return Map.of();
            }
            Map<String, ProductSummary> map = new HashMap<>(responses.length);
            for (ProductBatchResponse r : responses) {
                if (r != null && r.sku() != null) {
                    // map key を SKU にする (order_items.product_id == sku)
                    map.put(r.sku(), new ProductSummary(r.sku(), r.name(), r.categoryId()));
                }
            }
            return map;
        } catch (RestClientException ex) {
            log.warn("Failed to fetch products from inventory-service: {}", ex.getMessage());
            return Map.of();
        }
    }

    /**
     * カテゴリ ID → カテゴリ名の解決。
     * inventory-management-service の GET /api/v1/categories?size=200 を 1 回呼んでメモリ上でマップ化。
     */
    public Map<String, String> fetchCategoryNames() {
        try {
            CategoryPage page = restClient.get()
                    .uri(uri -> uri.path("/api/v1/categories").queryParam("size", 200).build())
                    .retrieve()
                    .body(CategoryPage.class);
            if (page == null || page.content() == null) {
                return Map.of();
            }
            Map<String, String> map = new HashMap<>(page.content().size());
            for (CategorySummary c : page.content()) {
                if (c != null && c.id() != null) {
                    map.put(c.id(), c.name());
                }
            }
            return map;
        } catch (RestClientException ex) {
            log.warn("Failed to fetch categories from inventory-service: {}", ex.getMessage());
            return Map.of();
        }
    }

    public void releaseReservation(String sku, int quantity, String referenceId, String reason) {
        try {
            restClient.post()
                    .uri("/api/v1/internal/inventory/release")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .body(new ReleaseReservationRequest(sku, quantity, reason, referenceId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "Failed to release inventory reservation for sku " + sku + " and reference " + referenceId, ex);
        }
    }

    public record ProductSummary(String sku, String name, String categoryId) {}

    public record ReleaseReservationRequest(String sku, int quantity, String reason, String referenceId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductBatchResponse(String id, String sku, String name, String categoryId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryPage(List<CategorySummary> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategorySummary(String id, String name) {}
}
