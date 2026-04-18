package com.example.skishop.payment.controller;

import com.example.skishop.payment.dto.BuildCartRequest;
import com.example.skishop.payment.dto.BuildCartResponse;
import com.example.skishop.payment.service.CartBuildService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Multi-Agent Orchestrator が呼び出す内部 API。
 * 直前に {@link com.example.skishop.common.security.InternalApiKeyAuthenticationFilter}
 * により {@code ROLE_AGENT} が付与される。
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartBuildController {

    private final CartBuildService service;

    public CartBuildController(CartBuildService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @PostMapping("/build")
    public ResponseEntity<BuildCartResponse> build(@Valid @RequestBody BuildCartRequest request) {
        return ResponseEntity.ok(service.build(request));
    }
}
