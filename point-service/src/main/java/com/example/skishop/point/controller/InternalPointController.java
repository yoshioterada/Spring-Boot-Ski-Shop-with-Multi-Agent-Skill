package com.example.skishop.point.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Multi-Agent Worker (PointServiceClient) 向け内部 API。
 * {@code X-Internal-Api-Key} で ROLE_AGENT 認証された呼び出し元のみ利用可能。
 *
 * <p>現状はスタブ実装。実装統合時は PointTransactionRepository/UserTierRepository から残高を集計する。
 */
@RestController
@RequestMapping("/api/v1/internal/points")
public class InternalPointController {

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/{userId}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String userId) {
        // TODO: PointTransactionRepository から sum(amount) を集計する。現状はスタブ。
        return ResponseEntity.ok(Map.of("userId", userId, "balance", 0));
    }
}
