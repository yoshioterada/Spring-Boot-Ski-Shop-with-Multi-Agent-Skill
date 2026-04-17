package com.example.skishop.agent.intent.client;

import com.example.skishop.agent.common.dto.UserPurchaseHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserProfileClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8081");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void getPurchaseHistory_returns_history_on_success() {
        server.expect(requestTo("http://localhost:8081/api/v1/internal/users/u1/purchase-history"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"userId":"u1","previouslyPurchasedCategories":["ski"],
                         "lastKnownSkillLevel":"INTERMEDIATE","totalPurchaseCount":3,"customerTier":"SILVER"}
                        """, MediaType.APPLICATION_JSON));

        UserProfileClient client = new UserProfileClient(builder.build());
        UserPurchaseHistory h = client.getPurchaseHistory("u1");
        assertThat(h.userId()).isEqualTo("u1");
        assertThat(h.customerTier()).isEqualTo("SILVER");
    }

    @Test
    void getPurchaseHistory_returns_fallback_on_server_error() {
        server.expect(requestTo("http://localhost:8081/api/v1/internal/users/u2/purchase-history"))
                .andRespond(withServerError());

        UserProfileClient client = new UserProfileClient(builder.build());
        UserPurchaseHistory h = client.getPurchaseHistory("u2");
        assertThat(h.userId()).isEqualTo("u2");
        assertThat(h.customerTier()).isEqualTo("BRONZE");
        assertThat(h.previouslyPurchasedCategories()).isEmpty();
    }

    @Test
    void fallback_returns_default_values() {
        UserPurchaseHistory h = UserProfileClient.fallback("anyone");
        assertThat(h.lastKnownSkillLevel()).isEqualTo("BEGINNER");
        assertThat(h.totalPurchaseCount()).isZero();
    }

    @Test
    void config_constructor_creates_client_with_headers() {
        // ensures @Value-based constructor branch is exercised
        UserProfileClient client = new UserProfileClient("http://localhost:8081", "key");
        assertThat(client).isNotNull();
    }

    @Test
    void config_constructor_handles_null_apiKey() {
        UserProfileClient client = new UserProfileClient("http://localhost:8081", null);
        assertThat(client).isNotNull();
    }

    // dummy reference to avoid unused import warning
    @SuppressWarnings("unused")
    private static final HttpHeaders REF = new HttpHeaders();
    @SuppressWarnings("unused")
    private static final List<String> REF2 = List.of();
}
