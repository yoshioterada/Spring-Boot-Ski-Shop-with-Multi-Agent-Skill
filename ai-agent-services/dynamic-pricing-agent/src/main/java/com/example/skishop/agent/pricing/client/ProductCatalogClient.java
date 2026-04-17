package com.example.skishop.agent.pricing.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * inventory-management-service / sales-management-service から商品マスタ情報を取得する。
 */
public class ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductCatalogClient(
            @Value("${services.inventory.base-url:http://localhost:8082}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "dynamic-pricing-agent")
                .build());
    }

    ProductCatalogClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public BigDecimal getBasePrice(String productId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("/api/v1/internal/products/{productId}", productId)
                    .retrieve()
                    .body(Map.class);
            if (body == null) return BigDecimal.ZERO;
            Object price = body.get("basePrice");
            if (price instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
            if (price != null) return new BigDecimal(price.toString());
            return BigDecimal.ZERO;
        } catch (RestClientException e) {
            log.warn("ベース価格取得失敗 productId={}: {}", productId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public int getStockCount(String productId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("/api/v1/internal/products/{productId}/stock", productId)
                    .retrieve()
                    .body(Map.class);
            if (body == null) return 0;
            Object qty = body.get("stockQuantity");
            if (qty instanceof Number n) return n.intValue();
            return 0;
        } catch (RestClientException e) {
            log.warn("在庫数取得失敗 productId={}: {}", productId, e.getMessage());
            return 0;
        }
    }
}
