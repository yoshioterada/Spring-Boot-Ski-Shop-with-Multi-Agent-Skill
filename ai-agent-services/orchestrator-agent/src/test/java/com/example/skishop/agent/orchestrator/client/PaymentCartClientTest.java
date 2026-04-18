package com.example.skishop.agent.orchestrator.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentCartClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private PaymentCartClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8084");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PaymentCartClient(builder.build());
    }

    @Test
    void buildCart_success() {
        server.expect(requestTo("http://localhost:8084/api/v1/cart/build"))
                .andRespond(withSuccess("{\"orderId\":\"o1\",\"status\":\"COMPLETED\"}",
                        MediaType.APPLICATION_JSON));
        Map<String, Object> r = client.buildCart("u1", "o1", "p1|板|1|45000",
                BigDecimal.valueOf(3000), null);
        assertThat(r).containsEntry("orderId", "o1").containsEntry("status", "COMPLETED");
    }

    @Test
    void buildCart_returns_failed_status_on_error() {
        server.expect(requestTo("http://localhost:8084/api/v1/cart/build"))
                .andRespond(withServerError());
        Map<String, Object> r = client.buildCart("u1", "o2", "p1|板|1|45000",
                null, null);
        assertThat(r).containsEntry("status", "FAILED");
    }

    @Test
    void parseCartItems_returns_empty_for_blank() {
        assertThat(PaymentCartClient.parseCartItems(null)).isEmpty();
        assertThat(PaymentCartClient.parseCartItems("")).isEmpty();
    }

    @Test
    void parseCartItems_throws_on_invalid_format() {
        assertThatThrownBy(() -> PaymentCartClient.parseCartItems("p1|name|1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseCartItems_calculates_lineTotal() {
        var items = PaymentCartClient.parseCartItems("p1|板|2|10000");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("lineTotal")).isEqualTo(BigDecimal.valueOf(20000));
    }

    @Test
    void config_constructor_handles_null_apikey() {
        assertThat(new PaymentCartClient("http://localhost:8084", null)).isNotNull();
    }

    @Test
    void buildCart_returns_empty_map_when_response_body_null() {
        // 200 OK but body parses to null → result == null branch
        server.expect(requestTo("http://localhost:8084/api/v1/cart/build"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        Map<String, Object> r = client.buildCart("u1", "o3", "p1|板|1|10000",
                BigDecimal.valueOf(0), BigDecimal.valueOf(500));
        assertThat(r).isEmpty();
    }

    @Test
    void parseCartItems_skips_blank_segments() {
        // ; separators with empty / whitespace segments are filtered
        var items = PaymentCartClient.parseCartItems(" ; p1|板|1|1000 ; ;");
        assertThat(items).hasSize(1);
    }

    @Test
    void config_constructor_with_explicit_apikey() {
        assertThat(new PaymentCartClient("http://localhost:8084", "my-api-key")).isNotNull();
    }
}
