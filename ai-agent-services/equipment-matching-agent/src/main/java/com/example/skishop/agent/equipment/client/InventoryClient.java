package com.example.skishop.agent.equipment.client;

import com.example.skishop.agent.common.dto.ProductCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * inventory-management-service から在庫候補を検索するクライアント。
 * 失敗時は空リストを返す（Worker は LLM ループで継続可能）。
 */
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryClient(
            @Value("${services.inventory.base-url:http://localhost:8082}") String inventoryBaseUrl,
            @Value("${services.internal-api-key:}") String internalApiKey) {
        this(RestClient.builder()
                .baseUrl(inventoryBaseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey == null ? "" : internalApiKey)
                .defaultHeader("X-Caller-Service", "equipment-matching-agent")
                .build());
    }

    InventoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ProductCandidate> searchBySkillAndCategory(String category, String skillLevel,
                                                            Integer maxBudgetYen) {
        try {
            var uriBuilder = UriComponentsBuilder.fromPath("/api/v1/internal/products/search")
                    .queryParam("category", category)
                    .queryParam("skillLevel", skillLevel);
            if (maxBudgetYen != null) {
                uriBuilder.queryParam("maxPrice", maxBudgetYen);
            }
            String uri = uriBuilder.build().toUriString();

            List<ProductCandidate> result = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ProductCandidate>>() {});
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            log.warn("在庫検索失敗 category={}, skill={}: {}", category, skillLevel, e.getMessage());
            return List.of();
        }
    }
}
