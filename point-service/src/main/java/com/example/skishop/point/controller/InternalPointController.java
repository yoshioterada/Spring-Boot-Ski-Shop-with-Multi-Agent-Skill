package com.example.skishop.point.controller;

import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.point.dto.PointBalanceResponse;
import com.example.skishop.point.service.PointService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Multi-Agent Worker (PointServiceClient) 向け内部 API。
 * {@code X-Internal-Api-Key} で ROLE_AGENT 認証された呼び出し元のみ利用可能。
 *
 */
@RestController
@RequestMapping("/api/v1/internal/points")
public class InternalPointController {

    private final PointService pointService;

    public InternalPointController(PointService pointService) {
        this.pointService = pointService;
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/{userId}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String userId) {
        PointBalanceResponse balance = getBalanceOrDefault(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("userId", userId, "balance", balance.currentBalance()));
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/{userId}/summary")
    public ResponseEntity<PointProfileSummary> getSummary(@PathVariable UUID userId) {
        PointBalanceResponse balance = getBalanceOrDefault(userId);
        return ResponseEntity.ok(new PointProfileSummary(
                userId,
                balance.tierLevel().name(),
                balance.tierName(),
                balance.currentBalance(),
                balance.totalEarned(),
                balance.totalRedeemed()));
    }

    private PointBalanceResponse getBalanceOrDefault(UUID userId) {
        try {
            return pointService.getBalance(userId);
        } catch (ResourceNotFoundException e) {
            return new PointBalanceResponse(userId, 0, 0, 0, 0,
                    com.example.skishop.point.model.TierDefinition.TierLevel.BRONZE,
                    "Bronze", 1.0, null, 0);
        }
    }

    public record PointProfileSummary(
            UUID userId,
            String tierLevel,
            String tierName,
            long pointBalance,
            long totalEarned,
            long totalRedeemed
    ) {}
}
