package com.example.skishop.agent.equipment.controller;

import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.equipment.service.EquipmentMatchingAgentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/equipment")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class EquipmentMatchingController {

    private final EquipmentMatchingAgentService service;

    public EquipmentMatchingController(EquipmentMatchingAgentService service) {
        this.service = service;
    }

    @PostMapping("/match")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<EquipmentMatchResult> match(
            @Valid @RequestBody EquipmentMatchRequest request) {
        return ResponseEntity.ok(service.match(request));
    }
}
