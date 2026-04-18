package com.example.skishop.sales.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Multi-Agent Worker (SalesManagementClient) 向け内部 API。
 * 動的価格決定エージェントが過去 N 日間の販売数を要求する。
 */
@RestController
@RequestMapping("/api/v1/internal/sales")
public class InternalSalesController {

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/{productId}/count")
    public ResponseEntity<Map<String, Object>> getSalesCount(
            @PathVariable String productId,
            @RequestParam(name = "days", defaultValue = "30") int days) {
        // TODO: OrderRepository.countByProductIdAndCreatedAtAfter を呼び出す。現状スタブ。
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "days", days,
                "count", 0));
    }
}
