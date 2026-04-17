package com.example.skishop.agent.pricing.controller;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.PricingRequest;
import com.example.skishop.agent.common.dto.PricingResult;
import com.example.skishop.agent.pricing.service.DynamicPricingAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/pricing")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class DynamicPricingController {

    private final DynamicPricingAgentService service;

    public DynamicPricingController(DynamicPricingAgentService service) {
        this.service = service;
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<PricingResult> calculate(@Valid @RequestBody PricingRequest request) {
        return ResponseEntity.ok(service.calculatePrice(request));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<List<PricingResult>> bulk(@Valid @RequestBody BulkPricingRequest request) {
        return ResponseEntity.ok(service.calculateBulkPrices(request));
    }
}
