package com.example.skishop.agent.inventory.controller;

import com.example.skishop.agent.common.dto.InventoryAnalysisResult;
import com.example.skishop.agent.common.dto.InventoryCheckRequest;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.inventory.service.InventoryMonitoringAgentService;
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
@RequestMapping("/api/v1/agents/inventory")
@ConditionalOnProperty(name = "agents.web.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryMonitoringController {

    private final InventoryMonitoringAgentService service;

    public InventoryMonitoringController(InventoryMonitoringAgentService service) {
        this.service = service;
    }

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<List<InventoryStatus>> check(@Valid @RequestBody InventoryCheckRequest request) {
        return ResponseEntity.ok(service.checkAndRoute(request));
    }

    /**
     * AI エージェントによる在庫分析。LLM がアラート分類・代替提案・サマリ生成を行う。
     */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<InventoryAnalysisResult> analyze(@Valid @RequestBody InventoryCheckRequest request) {
        return ResponseEntity.ok(service.analyzeInventory(request));
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public ResponseEntity<ReservationResult> reserve(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(service.reserve(request));
    }
}
