package com.example.skishop.payment.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.payment.dto.*;
import com.example.skishop.payment.dto.CartResponse.CartItemResponse;
import com.example.skishop.payment.model.Cart;
import com.example.skishop.payment.model.CartItem;
import com.example.skishop.payment.repository.CartItemRepository;
import com.example.skishop.payment.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EventPublisher eventPublisher;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       EventPublisher eventPublisher) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        log.info("Adding item to cart: userId={}, productId={}", userId, request.productId());
        Cart cart = getOrCreateCart(userId);

        var existingItem = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.productId()))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().updateQuantity(existingItem.get().getQuantity() + request.quantity());
            cart.recalculateTotal();
        } else {
            var item = new CartItem(request.productId(), request.productName(), request.quantity(), request.unitPrice());
            cart.addItem(item);
        }

        cart = cartRepository.save(cart);

        eventPublisher.publish(DomainEvent.create("CartItemAdded", "payment-service",
                new CartEventPayload(cart.getId(), userId, request.productId(), request.quantity())));

        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(UUID userId, UUID itemId, int quantity) {
        log.info("Updating cart item: userId={}, itemId={}, qty={}", userId, itemId, quantity);
        Cart cart = getOrCreateCart(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId.toString()));

        item.updateQuantity(quantity);
        cart.recalculateTotal();
        cart = cartRepository.save(cart);

        eventPublisher.publish(DomainEvent.create("CartItemUpdated", "payment-service",
                new CartEventPayload(cart.getId(), userId, item.getProductId(), quantity)));

        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID itemId) {
        Cart cart = getOrCreateCart(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId.toString()));

        cart.removeItem(item);
        cart = cartRepository.save(cart);

        eventPublisher.publish(DomainEvent.create("CartItemRemoved", "payment-service",
                new CartEventPayload(cart.getId(), userId, item.getProductId(), 0)));

        return toResponse(cart);
    }

    @Transactional
    public void clearCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        cart.clearItems();
        cartRepository.save(cart);

        eventPublisher.publish(DomainEvent.create("CartCleared", "payment-service",
                new CartEventPayload(cart.getId(), userId, null, 0)));
    }

    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(new Cart(userId)));
    }

    private CartResponse toResponse(Cart cart) {
        var items = cart.getItems().stream()
                .map(i -> new CartItemResponse(i.getId(), i.getProductId(), i.getProductName(),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalPrice()))
                .toList();
        return new CartResponse(cart.getId(), cart.getUserId(), cart.getTotalAmount(),
                cart.getCurrency(), items, cart.getUpdatedAt());
    }

    public record CartEventPayload(UUID cartId, UUID userId, String productId, int quantity) {}
}
