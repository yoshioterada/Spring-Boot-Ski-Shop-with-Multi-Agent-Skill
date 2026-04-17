package com.example.skishop.agent.orchestrator.controller;

import com.example.skishop.agent.orchestrator.dto.OrchestratorRequest;
import com.example.skishop.agent.orchestrator.dto.OrchestratorResponse;
import com.example.skishop.agent.orchestrator.service.OrchestratorAgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orchestrator")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class OrchestratorController {

    private final OrchestratorAgentService service;

    public OrchestratorController(OrchestratorAgentService service) {
        this.service = service;
    }

    @PostMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<OrchestratorResponse> recommend(
            @Valid @RequestBody OrchestratorRequest request,
            HttpServletRequest httpRequest) {
        String jwtToken = extractBearerToken(httpRequest);
        return ResponseEntity.ok(service.orchestrate(request, jwtToken));
    }

    static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return "";
    }
}
