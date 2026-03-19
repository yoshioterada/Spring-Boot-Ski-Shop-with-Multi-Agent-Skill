package com.example.skishop.payment.controller;

import com.example.skishop.payment.dto.*;
import com.example.skishop.payment.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestParam UUID userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@RequestParam UUID userId,
                                                 @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(userId, request));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(@RequestParam UUID userId,
                                                    @PathVariable UUID itemId,
                                                    @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(userId, itemId, request.quantity()));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(@RequestParam UUID userId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(cartService.removeItem(userId, itemId));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @DeleteMapping
    public ResponseEntity<Void> clearCart(@RequestParam UUID userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
