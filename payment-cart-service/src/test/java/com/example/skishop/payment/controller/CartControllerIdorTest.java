package com.example.skishop.payment.controller;

import com.example.skishop.payment.config.SecurityConfig;
import com.example.skishop.payment.dto.AddCartItemRequest;
import com.example.skishop.payment.dto.CartResponse;
import com.example.skishop.payment.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-testing-only-minimum-256-bits-required-here-padding")
class CartControllerIdorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken ownerAuth() {
        return new UsernamePasswordAuthenticationToken(
                ownerId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private CartResponse emptyCart(UUID userId) {
        return new CartResponse(UUID.randomUUID(), userId, BigDecimal.ZERO, "JPY", List.of(), Instant.now());
    }

    @Nested
    @DisplayName("GET /api/v1/cart — IDOR防止")
    class GetCart {

        @Test
        @DisplayName("自分のカートを取得できる")
        void should_allowAccess_when_ownerAccesses() throws Exception {
            when(cartService.getCart(ownerId)).thenReturn(emptyCart(ownerId));

            mockMvc.perform(get("/api/v1/cart")
                            .param("userId", ownerId.toString())
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("他人のカートは取得できない（403）")
        void should_denyAccess_when_otherUserAccesses() throws Exception {
            mockMvc.perform(get("/api/v1/cart")
                            .param("userId", otherUserId.toString())
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN権限で他人のカートを取得できる")
        void should_allowAccess_when_adminAccesses() throws Exception {
            when(cartService.getCart(otherUserId)).thenReturn(emptyCart(otherUserId));

            mockMvc.perform(get("/api/v1/cart")
                            .param("userId", otherUserId.toString())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/cart/items — IDOR防止")
    class AddItem {

        @Test
        @DisplayName("自分のカートに商品を追加できる")
        void should_allowAdd_when_ownerAdds() throws Exception {
            var request = new AddCartItemRequest("prod-001", "スキーブーツ", 2, new BigDecimal("15000"));
            when(cartService.addItem(ownerId, request)).thenReturn(emptyCart(ownerId));

            mockMvc.perform(post("/api/v1/cart/items")
                            .param("userId", ownerId.toString())
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("他人のカートに商品を追加できない（403）")
        void should_denyAdd_when_otherUserAdds() throws Exception {
            var request = new AddCartItemRequest("prod-001", "スキーブーツ", 2, new BigDecimal("15000"));

            mockMvc.perform(post("/api/v1/cart/items")
                            .param("userId", otherUserId.toString())
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/cart — IDOR防止")
    class ClearCart {

        @Test
        @DisplayName("他人のカートはクリアできない（403）")
        void should_denyClear_when_otherUserClears() throws Exception {
            mockMvc.perform(delete("/api/v1/cart")
                            .param("userId", otherUserId.toString())
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("未認証アクセス")
    class Unauthenticated {

        @Test
        @DisplayName("未認証ユーザーはアクセスできない（403）")
        void should_denyAccess_when_unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/cart")
                            .param("userId", ownerId.toString()))
                    .andExpect(status().isForbidden());
        }
    }
}
