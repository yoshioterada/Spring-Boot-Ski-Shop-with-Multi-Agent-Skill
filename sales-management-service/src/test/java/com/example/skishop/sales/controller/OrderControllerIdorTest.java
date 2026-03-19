package com.example.skishop.sales.controller;

import com.example.skishop.sales.config.SecurityConfig;
import com.example.skishop.sales.dto.OrderResponse;
import com.example.skishop.sales.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-testing-only-minimum-256-bits-required-here-padding")
class OrderControllerIdorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherCustomerId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken ownerAuth() {
        return new UsernamePasswordAuthenticationToken(
                ownerId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private OrderResponse createOrderResponse(UUID customerId) {
        return new OrderResponse(
                UUID.randomUUID(), "ORD-001", customerId, "PENDING", "PENDING",
                BigDecimal.valueOf(10000), BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(11000), "JPY", null, "東京都新宿区1-1-1",
                List.of(), Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("GET /api/v1/orders/customer/{customerId} — IDOR防止")
    class GetCustomerOrders {

        @Test
        @DisplayName("自分の注文履歴を取得できる")
        void should_allowAccess_when_ownerAccesses() throws Exception {
            when(orderService.getCustomerOrders(eq(ownerId), any()))
                    .thenReturn(new PageImpl<>(List.of(createOrderResponse(ownerId))));

            mockMvc.perform(get("/api/v1/orders/customer/{customerId}", ownerId)
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("他人の注文履歴は取得できない（403）")
        void should_denyAccess_when_otherUserAccesses() throws Exception {
            mockMvc.perform(get("/api/v1/orders/customer/{customerId}", otherCustomerId)
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN権限で他人の注文履歴を取得できる")
        void should_allowAccess_when_adminAccesses() throws Exception {
            when(orderService.getCustomerOrders(eq(otherCustomerId), any()))
                    .thenReturn(new PageImpl<>(List.of(createOrderResponse(otherCustomerId))));

            mockMvc.perform(get("/api/v1/orders/customer/{customerId}", otherCustomerId)
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/orders/{orderId}/cancel — IDOR防止")
    class CancelOrder {

        @Test
        @DisplayName("自分の注文をキャンセルできる")
        void should_allowCancel_when_ownerCancels() throws Exception {
            UUID orderId = UUID.randomUUID();
            when(orderService.cancelOrder(orderId, "顧客都合"))
                    .thenReturn(createOrderResponse(ownerId));

            mockMvc.perform(put("/api/v1/orders/{orderId}/cancel", orderId)
                            .param("reason", "顧客都合")
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("未認証アクセス")
    class Unauthenticated {

        @Test
        @DisplayName("未認証ユーザーは注文履歴にアクセスできない（403）")
        void should_denyAccess_when_unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/orders/customer/{customerId}", ownerId))
                    .andExpect(status().isForbidden());
        }
    }
}
