package com.example.skishop.agent.pricing.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductCatalogClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ProductCatalogClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProductCatalogClient(builder.build());
    }

    @Test
    void getBasePrice_success_with_number() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/p1"))
                .andRespond(withSuccess("{\"basePrice\":12345.67}", MediaType.APPLICATION_JSON));
        assertThat(client.getBasePrice("p1")).isEqualByComparingTo("12345.67");
    }

    @Test
    void getBasePrice_returns_zero_on_error() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/p2"))
                .andRespond(withServerError());
        assertThat(client.getBasePrice("p2")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getBasePrice_returns_zero_when_field_missing() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/p3"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(client.getBasePrice("p3")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getStockCount_success() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/p1/stock"))
                .andRespond(withSuccess("{\"stockQuantity\":42}", MediaType.APPLICATION_JSON));
        assertThat(client.getStockCount("p1")).isEqualTo(42);
    }

    @Test
    void getStockCount_returns_zero_on_error() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/p2/stock"))
                .andRespond(withServerError());
        assertThat(client.getStockCount("p2")).isZero();
    }

    @Test
    void getStockCount_returns_zero_when_missing() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/products/p3/stock"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(client.getStockCount("p3")).isZero();
    }

    @Test
    void config_constructor_handles_null_apikey() {
        assertThat(new ProductCatalogClient("http://localhost:8082", null)).isNotNull();
    }
}
