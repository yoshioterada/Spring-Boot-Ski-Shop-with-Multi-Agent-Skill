package com.example.skishop.agent.inventory.client;

import com.example.skishop.agent.common.dto.InventoryAlert;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

/**
 * inventory-management-service への RestClient ラッパー。
 * 失敗時は欠損なき安全値を返す。
 */
public class InventoryManagementClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryManagementClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryManagementClient(
            @Value("${services.inventory.base-url:http://localhost:8082}") String baseUrl,
            @Value("${services.internal-api-key:}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey == null ? "" : apiKey)
                .defaultHeader("X-Caller-Service", "inventory-monitoring-agent")
                .build());
    }

    InventoryManagementClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public InventoryStatus getStock(String productId) {
        try {
            InventoryStatus status = restClient.get()
                    .uri("/api/v1/internal/inventory/{productId}", productId)
                    .retrieve()
                    .body(InventoryStatus.class);
            return status != null ? status : unknown(productId);
        } catch (RestClientException e) {
            log.warn("在庫取得失敗 productId={}: {}", productId, e.getMessage());
            return unknown(productId);
        }
    }

    public ReservationResult reserve(ReservationRequest request) {
        try {
            ReservationResult result = restClient.post()
                    .uri("/api/v1/internal/inventory/reserve")
                    .body(request)
                    .retrieve()
                    .body(ReservationResult.class);
            return result != null ? result : reservationFailure(request);
        } catch (RestClientException e) {
            log.warn("予約失敗 orderId={}: {}", request.orderId(), e.getMessage());
            return reservationFailure(request);
        }
    }

    public List<InventoryAlert> getLowStockItems(int threshold) {
        try {
            List<InventoryAlert> alerts = restClient.get()
                    .uri("/api/v1/internal/inventory/alerts?threshold={t}", threshold)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InventoryAlert>>() {});
            return alerts != null ? alerts : List.of();
        } catch (RestClientException e) {
            log.warn("低在庫アラート取得失敗: {}", e.getMessage());
            return List.of();
        }
    }

    public List<String> findAlternatives(String productId, String category, String skillLevel) {
        try {
            List<String> ids = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/internal/inventory/{productId}/alternatives")
                            .queryParam("category", category)
                            .queryParam("skillLevel", skillLevel)
                            .build(productId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<String>>() {});
            return ids != null ? ids : List.of();
        } catch (RestClientException e) {
            log.warn("代替商品取得失敗 productId={}: {}", productId, e.getMessage());
            return List.of();
        }
    }

    static InventoryStatus unknown(String productId) {
        return new InventoryStatus(productId, "(unknown)", 0, "OUT_OF_STOCK", false, null, List.of(), null);
    }

    static ReservationResult reservationFailure(ReservationRequest request) {
        List<String> failedIds = request.items().stream()
                .map(ReservationRequest.ReservationItem::productId).toList();
        return new ReservationResult(null, request.orderId(), false, List.of(), failedIds, Instant.now());
    }
}
