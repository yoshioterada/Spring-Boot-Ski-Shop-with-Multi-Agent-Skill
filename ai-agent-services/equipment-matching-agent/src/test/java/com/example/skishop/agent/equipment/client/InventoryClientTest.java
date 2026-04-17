package com.example.skishop.agent.equipment.client;

import com.example.skishop.agent.common.dto.ProductCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InventoryClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8082");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void searchBySkillAndCategory_returns_list_on_success() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/search?category=%E3%82%B9%E3%82%AD%E3%83%BC%E6%9D%BF&skillLevel=BEGINNER&maxPrice=50000"))
                .andRespond(withSuccess("""
                        [{"productId":"p1","productName":"Ski","category":"スキー板","brand":"X",
                         "basePrice":30000,"isAvailable":true,"stockQuantity":5,
                         "skillLevelSuitability":"BEGINNER","weatherSuitability":"ALL_CONDITIONS","attributes":{}}]
                        """, MediaType.APPLICATION_JSON));

        var client = new InventoryClient(builder.build());
        var result = client.searchBySkillAndCategory("スキー板", "BEGINNER", 50000);
        assertThat(result).hasSize(1).extracting(ProductCandidate::productId).containsExactly("p1");
    }

    @Test
    void searchBySkillAndCategory_returns_empty_on_error() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/search?category=ski&skillLevel=BEGINNER"))
                .andRespond(withServerError());

        var client = new InventoryClient(builder.build());
        assertThat(client.searchBySkillAndCategory("ski", "BEGINNER", null)).isEmpty();
    }

    @Test
    void config_constructor_handles_null_apikey() {
        var client = new InventoryClient("http://localhost:8082", null);
        assertThat(client).isNotNull();
    }

    @Test
    void config_constructor_with_apikey() {
        var client = new InventoryClient("http://localhost:8082", "key");
        assertThat(client).isNotNull();
    }
}
