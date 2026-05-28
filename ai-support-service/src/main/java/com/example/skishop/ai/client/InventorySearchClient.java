package com.example.skishop.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class InventorySearchClient {

    private final WebClient inventoryWebClient;

    public InventorySearchClient(@Qualifier("inventoryWebClient") WebClient inventoryWebClient) {
        this.inventoryWebClient = inventoryWebClient;
    }

    public InventorySearchPage search(String query, String enhancedQuery, String source,
                                      String category, int page, int size) {
        return inventoryWebClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/v1/products/search")
                            .queryParam("q", query)
                            .queryParam("enhancedQuery", enhancedQuery)
                            .queryParam("source", source)
                            .queryParam("page", Math.max(0, page))
                            .queryParam("size", Math.max(1, size));
                    if (category != null && !category.isBlank()) {
                        builder.queryParam("category", category.trim());
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<InventorySearchPage>() {})
                .blockOptional()
                .orElseGet(() -> new InventorySearchPage(List.of(), 0));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InventorySearchPage(List<InventoryProduct> content, long totalElements) {
        public InventorySearchPage {
            content = content == null ? List.of() : content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InventoryProduct(
            String id,
            String sku,
            String name,
            String description,
            String brand,
            String categoryId,
            int stockQuantity,
            int availableQuantity,
            String status
    ) {}
}