package com.example.skishop.agent.inventory.client;

import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InventoryManagementClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private InventoryManagementClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InventoryManagementClient(builder.build());
    }

    @Test
    void getStock_success() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/p1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"productId":"p1","productName":"Ski","stockQuantity":10,
                         "availabilityStatus":"AVAILABLE","isReservable":true,
                         "estimatedRestockDate":null,"alternativeProductIds":[],"alert":null}
                        """, MediaType.APPLICATION_JSON));

        InventoryStatus s = client.getStock("p1");
        assertThat(s.stockQuantity()).isEqualTo(10);
    }

    @Test
    void getStock_returns_unknown_on_error() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/p2"))
                .andRespond(withServerError());

        InventoryStatus s = client.getStock("p2");
        assertThat(s.productId()).isEqualTo("p2");
        assertThat(s.stockQuantity()).isZero();
        assertThat(s.availabilityStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void reserve_success() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"reservationId":"r1","orderId":"o1","isFullyReserved":true,
                         "reservedProductIds":["p1"],"failedProductIds":[],"expiresAt":"2026-04-17T10:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        var req = new ReservationRequest("o1", "u1",
                List.of(new ReservationRequest.ReservationItem("p1", 1)), 30);
        ReservationResult r = client.reserve(req);
        assertThat(r.isFullyReserved()).isTrue();
    }

    @Test
    void reserve_returns_failure_on_error() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/reserve"))
                .andRespond(withServerError());

        var req = new ReservationRequest("o1", "u1",
                List.of(new ReservationRequest.ReservationItem("p1", 1)), 30);
        ReservationResult r = client.reserve(req);
        assertThat(r.isFullyReserved()).isFalse();
        assertThat(r.failedProductIds()).containsExactly("p1");
    }

    @Test
    void getLowStockItems_returns_empty_on_error() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/alerts?threshold=5"))
                .andRespond(withServerError());
        assertThat(client.getLowStockItems(5)).isEmpty();
    }

    @Test
    void findAlternatives_success() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/p1/alternatives?category=ski&skillLevel=BEGINNER"))
                .andRespond(withSuccess("[\"p2\",\"p3\"]", MediaType.APPLICATION_JSON));
        assertThat(client.findAlternatives("p1", "ski", "BEGINNER")).containsExactly("p2", "p3");
    }

    @Test
    void findAlternatives_empty_on_error() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/x/alternatives?category=ski&skillLevel=BEGINNER"))
                .andRespond(withServerError());
        assertThat(client.findAlternatives("x", "ski", "BEGINNER")).isEmpty();
    }

    @Test
    void unknown_helper_returns_safe_default() {
        InventoryStatus s = InventoryManagementClient.unknown("z");
        assertThat(s.productId()).isEqualTo("z");
        assertThat(s.availabilityStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void reservationFailure_helper_collects_failed_ids() {
        var req = new ReservationRequest("o1", "u1",
                List.of(new ReservationRequest.ReservationItem("p1", 1),
                        new ReservationRequest.ReservationItem("p2", 1)), 30);
        ReservationResult r = InventoryManagementClient.reservationFailure(req);
        assertThat(r.failedProductIds()).containsExactly("p1", "p2");
        assertThat(r.expiresAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void config_constructor_handles_null_apikey() {
        var c = new InventoryManagementClient("http://localhost:8082", null);
        assertThat(c).isNotNull();
    }

    @Test
    void config_constructor_with_apikey() {
        assertThat(new InventoryManagementClient("http://localhost:8082", "key")).isNotNull();
    }

    @Test
    void getStock_returns_unknown_when_body_is_null() {
        // 200 OK but body parses to null → status == null branch
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/p9"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        var s = client.getStock("p9");
        assertThat(s.availabilityStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void reserve_returns_failure_when_body_is_null() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/reserve"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        var req = new ReservationRequest("o9", "u9",
                List.of(new ReservationRequest.ReservationItem("p9", 2)), 30);
        var r = client.reserve(req);
        assertThat(r.isFullyReserved()).isFalse();
        assertThat(r.failedProductIds()).containsExactly("p9");
    }

    @Test
    void getLowStockItems_success_path() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/alerts?threshold=3"))
                .andRespond(withSuccess("""
                        [{"productId":"p1","productName":"Ski","currentStock":2,"threshold":3,
                          "severity":"WARNING","detectedAt":"2026-04-17T10:00:00Z"}]
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.getLowStockItems(3)).hasSize(1);
    }

    @Test
    void getLowStockItems_returns_empty_when_body_is_null() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/alerts?threshold=1"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        assertThat(client.getLowStockItems(1)).isEmpty();
    }

    @Test
    void findAlternatives_returns_empty_when_body_is_null() {
        server.expect(requestTo("http://localhost:8082/api/v1/internal/inventory/p1/alternatives?category=ski&skillLevel=BEGINNER"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        assertThat(client.findAlternatives("p1", "ski", "BEGINNER")).isEmpty();
    }
}
