package com.example.skishop.payment.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.payment.dto.AddCartItemRequest;
import com.example.skishop.payment.dto.CartResponse;
import com.example.skishop.payment.model.Cart;
import com.example.skishop.payment.model.CartItem;
import com.example.skishop.payment.repository.CartItemRepository;
import com.example.skishop.payment.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private EventPublisher eventPublisher;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, eventPublisher);
    }

    @Nested
    @DisplayName("カート取得")
    class GetCart {

        @Test
        @DisplayName("既存カートが存在する場合、カートを返す")
        void should_returnCart_when_cartExists() {
            UUID userId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

            CartResponse response = cartService.getCart(userId);

            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.items()).isEmpty();
        }

        @Test
        @DisplayName("カートが存在しない場合、新規カートを作成して返す")
        void should_createNewCart_when_cartNotExists() {
            UUID userId = UUID.randomUUID();
            Cart newCart = new Cart(userId);
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(newCart);

            CartResponse response = cartService.getCart(userId);

            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("商品追加")
    class AddItem {

        @Test
        @DisplayName("カートに新規商品を追加できる")
        void should_addNewItem_when_itemNotInCart() {
            UUID userId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            var request = new AddCartItemRequest("prod-001", "スキーブーツ", 2, new BigDecimal("15000"));
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.addItem(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.items()).hasSize(1);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("既存商品を追加した場合、数量が加算される")
        void should_incrementQuantity_when_existingItemAdded() {
            UUID userId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            cart.addItem(new CartItem("prod-001", "スキーブーツ", 1, new BigDecimal("15000")));
            var request = new AddCartItemRequest("prod-001", "スキーブーツ", 2, new BigDecimal("15000"));
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.addItem(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().getFirst().quantity()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("数量更新")
    class UpdateItem {

        @Test
        @DisplayName("存在する商品の数量を更新でき、CartItemUpdatedイベントが発行される")
        void should_updateQuantity_when_itemExists() {
            UUID userId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            CartItem item = new CartItem("prod-001", "スキーブーツ", 1, new BigDecimal("15000"));
            setCartItemId(item, itemId);
            cart.addItem(item);
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.updateItem(userId, itemId, 5);

            assertThat(response).isNotNull();
            assertThat(response.items().getFirst().quantity()).isEqualTo(5);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("存在しない商品を更新しようとした場合、ResourceNotFoundExceptionがスローされる")
        void should_throwResourceNotFound_when_updateNonExistentItem() {
            UUID userId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> cartService.updateItem(userId, itemId, 5))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("商品削除")
    class RemoveItem {

        @Test
        @DisplayName("カートから商品を削除できる")
        void should_removeItem_when_itemExists() {
            UUID userId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            CartItem item = new CartItem("prod-001", "スキーブーツ", 1, new BigDecimal("15000"));
            setCartItemId(item, itemId);
            cart.addItem(item);
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.removeItem(userId, itemId);

            assertThat(response).isNotNull();
            assertThat(response.items()).isEmpty();
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("存在しない商品を削除しようとした場合、ResourceNotFoundExceptionがスローされる")
        void should_throwResourceNotFound_when_removeNonExistentItem() {
            UUID userId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> cartService.removeItem(userId, itemId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("カートクリア")
    class ClearCart {

        @Test
        @DisplayName("カートをクリアできる")
        void should_clearCart_when_called() {
            UUID userId = UUID.randomUUID();
            Cart cart = new Cart(userId);
            cart.addItem(new CartItem("prod-001", "スキーブーツ", 1, new BigDecimal("15000")));
            when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            cartService.clearCart(userId);

            verify(cartRepository).save(any(Cart.class));
            verify(eventPublisher).publish(any());
        }
    }

    private void setCartItemId(CartItem item, UUID id) {
        try {
            Field idField = CartItem.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(item, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
