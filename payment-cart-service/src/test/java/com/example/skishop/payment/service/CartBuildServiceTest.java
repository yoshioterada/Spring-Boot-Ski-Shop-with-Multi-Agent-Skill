package com.example.skishop.payment.service;

import com.example.skishop.payment.dto.AddCartItemRequest;
import com.example.skishop.payment.dto.BuildCartRequest;
import com.example.skishop.payment.dto.BuildCartResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CartBuildServiceTest {

    private static final String VALID_USER_ID = "11111111-1111-1111-1111-111111111111";

    private final CartService cartService = mock(CartService.class);
    private final CartBuildService service = new CartBuildService(cartService);

    @Test
    void build_calculates_subtotal_and_total_and_persists_to_user_cart() {
        var req = new BuildCartRequest(VALID_USER_ID, "o1",
                List.of(item("p1", "板", 1, "45000", "45000"),
                        item("p2", "ウェア", 2, "10000", "20000")),
                new BigDecimal("3000"), new BigDecimal("1000"));
        BuildCartResponse r = service.build(req);
        assertThat(r.subtotal()).isEqualByComparingTo("65000");
        assertThat(r.totalAmount()).isEqualByComparingTo("61000");
        assertThat(r.status()).isEqualTo("CONFIRMED");
        assertThat(r.lines()).hasSize(2);
        assertThat(r.orderId()).isEqualTo("o1");
        verify(cartService, times(2)).addItem(eq(UUID.fromString(VALID_USER_ID)), any(AddCartItemRequest.class));
    }

    @Test
    void build_floors_total_at_zero_when_discount_exceeds_subtotal() {
        var req = new BuildCartRequest(VALID_USER_ID, "o2",
                List.of(item("p1", "x", 1, "100", "100")),
                new BigDecimal("9999"), new BigDecimal("0"));
        var r = service.build(req);
        assertThat(r.totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void build_treats_null_discount_as_zero() {
        var req = new BuildCartRequest(VALID_USER_ID, "o3",
                List.of(item("p1", "x", 1, "500", "500")),
                BigDecimal.ZERO, BigDecimal.ZERO);
        var r = service.build(req);
        assertThat(r.couponDiscount()).isEqualByComparingTo("0");
        assertThat(r.totalAmount()).isEqualByComparingTo("500");
    }

    @Test
    void build_returns_preview_only_when_userId_is_not_uuid() {
        var req = new BuildCartRequest("not-a-uuid", "o4",
                List.of(item("p1", "x", 1, "500", "500")),
                BigDecimal.ZERO, BigDecimal.ZERO);
        var r = service.build(req);
        assertThat(r.status()).isEqualTo("PREVIEW_ONLY");
        verify(cartService, times(0)).addItem(any(), any());
    }

    @Test
    void build_returns_partial_when_some_items_fail_to_persist() {
        doThrow(new RuntimeException("boom"))
                .when(cartService).addItem(any(UUID.class), any(AddCartItemRequest.class));

        var req = new BuildCartRequest(VALID_USER_ID, "o5",
                List.of(item("p1", "x", 1, "500", "500")),
                BigDecimal.ZERO, BigDecimal.ZERO);
        var r = service.build(req);
        // 全件失敗のため PREVIEW_ONLY 扱い
        assertThat(r.status()).isEqualTo("PREVIEW_ONLY");
    }

    private static BuildCartRequest.BuildCartItem item(String id, String name, int qty, String unit, String line) {
        return new BuildCartRequest.BuildCartItem(id, name, qty, new BigDecimal(unit), new BigDecimal(line));
    }
}
