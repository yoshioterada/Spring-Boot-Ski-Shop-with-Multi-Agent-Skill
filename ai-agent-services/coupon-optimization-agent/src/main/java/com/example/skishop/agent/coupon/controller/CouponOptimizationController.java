package com.example.skishop.agent.coupon.controller;

import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
import com.example.skishop.agent.coupon.service.CouponOptimizationAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/coupon")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class CouponOptimizationController {

    private final CouponOptimizationAgentService service;

    public CouponOptimizationController(CouponOptimizationAgentService service) {
        this.service = service;
    }

    @PostMapping("/optimize")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<CouponOptimizationResult> optimize(
            @Valid @RequestBody CouponOptimizationRequest request) {
        return ResponseEntity.ok(service.optimize(request));
    }
}
