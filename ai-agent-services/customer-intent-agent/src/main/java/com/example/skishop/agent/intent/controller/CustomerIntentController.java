package com.example.skishop.agent.intent.controller;

import com.example.skishop.agent.common.dto.CustomerIntentRequest;
import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.intent.service.CustomerIntentAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/intent")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class CustomerIntentController {

    private final CustomerIntentAgentService service;

    public CustomerIntentController(CustomerIntentAgentService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<CustomerIntentResult> analyze(
            @Valid @RequestBody CustomerIntentRequest request) {
        return ResponseEntity.ok(service.analyze(request));
    }
}
