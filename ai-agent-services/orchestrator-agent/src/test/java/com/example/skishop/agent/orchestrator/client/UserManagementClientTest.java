package com.example.skishop.agent.orchestrator.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserManagementClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private UserManagementClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UserManagementClient(builder.build());
    }

    @Test
    void getUserProfile_success() {
        server.expect(requestTo("http://localhost:8081/api/v1/users/u1/profile"))
                .andRespond(withSuccess("""
                        {"userId":"u1","displayName":"Alice","customerTier":"GOLD",
                         "purchasedCategories":["スキー板"],"preferredSkillLevel":"INTERMEDIATE",
                         "pointBalance":1000}
                        """, MediaType.APPLICATION_JSON));
        var p = client.getUserProfile("u1", "tok");
        assertThat(p.customerTier()).isEqualTo("GOLD");
        assertThat(p.pointBalance()).isEqualTo(1000);
    }

    @Test
    void getUserProfile_returns_fallback_on_error() {
        server.expect(requestTo("http://localhost:8081/api/v1/users/u2/profile"))
                .andRespond(withServerError());
        var p = client.getUserProfile("u2", "tok");
        assertThat(p.customerTier()).isEqualTo("BRONZE");
        assertThat(p.purchasedCategories()).isEmpty();
    }

    @Test
    void getUserProfile_handles_null_token() {
        server.expect(requestTo("http://localhost:8081/api/v1/users/u3/profile"))
                .andRespond(withServerError());
        var p = client.getUserProfile("u3", null);
        assertThat(p.userId()).isEqualTo("u3");
    }

    @Test
    void config_constructor_runs() {
        assertThat(new UserManagementClient("http://localhost:8081")).isNotNull();
    }

    @Test
    void fallback_static_helper() {
        assertThat(UserManagementClient.fallback("uX").customerTier()).isEqualTo("BRONZE");
    }
}
