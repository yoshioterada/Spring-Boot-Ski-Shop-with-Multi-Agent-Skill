package com.example.skishop.agent.coupon.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PointServiceClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private PointServiceClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8087");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PointServiceClient(builder.build());
    }

    @Test
    void getPointBalance_success() {
        server.expect(requestTo("http://localhost:8087/api/v1/internal/points/u1/balance"))
                .andRespond(withSuccess("{\"balance\":1500}", MediaType.APPLICATION_JSON));
        assertThat(client.getPointBalance("u1")).isEqualTo(1500);
    }

    @Test
    void getPointBalance_returns_zero_on_error() {
        server.expect(requestTo("http://localhost:8087/api/v1/internal/points/u2/balance"))
                .andRespond(withServerError());
        assertThat(client.getPointBalance("u2")).isZero();
    }

    @Test
    void getPointBalance_returns_zero_when_missing() {
        server.expect(requestTo("http://localhost:8087/api/v1/internal/points/u3/balance"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(client.getPointBalance("u3")).isZero();
    }

    @Test
    void config_constructor_handles_null_apikey() {
        assertThat(new PointServiceClient("http://localhost:8087", null)).isNotNull();
    }
}
