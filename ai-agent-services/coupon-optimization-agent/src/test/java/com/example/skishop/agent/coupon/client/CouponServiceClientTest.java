package com.example.skishop.agent.coupon.client;

import com.example.skishop.agent.common.dto.CouponCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CouponServiceClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private CouponServiceClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8086");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CouponServiceClient(builder.build());
    }

    @Test
    void getUserCoupons_success() {
        server.expect(requestTo("http://localhost:8086/api/v1/internal/coupons/users/u1"))
                .andRespond(withSuccess("""
                        [{"couponId":"c1","couponCode":"WELCOME","couponType":"PERCENTAGE",
                          "discountRate":0.1,"discountAmount":0,"minimumOrder":0,
                          "applicableCategory":null,"expiresAt":"2099-01-01",
                          "isStackable":true,"usageLimit":1}]
                        """, MediaType.APPLICATION_JSON));
        var coupons = client.getUserCoupons("u1");
        assertThat(coupons).hasSize(1)
                .extracting(CouponCandidate::couponCode).containsExactly("WELCOME");
    }

    @Test
    void getUserCoupons_returns_empty_on_error() {
        server.expect(requestTo("http://localhost:8086/api/v1/internal/coupons/users/u2"))
                .andRespond(withServerError());
        assertThat(client.getUserCoupons("u2")).isEmpty();
    }

    @Test
    void findByCouponCode_success() {
        server.expect(requestTo("http://localhost:8086/api/v1/internal/coupons/by-code/X1"))
                .andRespond(withSuccess("""
                        {"couponId":"c2","couponCode":"X1","couponType":"FIXED_AMOUNT",
                         "discountRate":0,"discountAmount":500,"minimumOrder":0,
                         "applicableCategory":null,"expiresAt":"2099-01-01",
                         "isStackable":false,"usageLimit":1}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.findByCouponCode("X1")).isPresent();
    }

    @Test
    void findByCouponCode_empty_on_error() {
        server.expect(requestTo("http://localhost:8086/api/v1/internal/coupons/by-code/X2"))
                .andRespond(withServerError());
        assertThat(client.findByCouponCode("X2")).isEmpty();
    }

    @Test
    void config_constructor_handles_null_apikey() {
        assertThat(new CouponServiceClient("http://localhost:8086", null)).isNotNull();
    }
}
