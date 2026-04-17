package com.example.skishop.agent.pricing.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SalesManagementClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SalesManagementClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8085");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SalesManagementClient(builder.build());
    }

    @Test
    void getSalesCount_success() {
        server.expect(requestTo("http://localhost:8085/api/v1/internal/sales/p1/count?days=7"))
                .andRespond(withSuccess("{\"count\":25}", MediaType.APPLICATION_JSON));
        assertThat(client.getSalesCount("p1", 7)).isEqualTo(25);
    }

    @Test
    void getSalesCount_returns_zero_on_error() {
        server.expect(requestTo("http://localhost:8085/api/v1/internal/sales/p2/count?days=7"))
                .andRespond(withServerError());
        assertThat(client.getSalesCount("p2", 7)).isZero();
    }

    @Test
    void getSalesCount_returns_zero_when_missing() {
        server.expect(requestTo("http://localhost:8085/api/v1/internal/sales/p3/count?days=7"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(client.getSalesCount("p3", 7)).isZero();
    }

    @Test
    void config_constructor_handles_null_apikey() {
        assertThat(new SalesManagementClient("http://localhost:8085", null)).isNotNull();
    }
}
