package com.example.skishop.coupon.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Multi-Agent Worker (CouponServiceClient) 向け内部 API。
 * 既存の Campaign/Coupon/UserCoupon リポジトリと連携予定だが、
 * 現フェーズでは契約合致のためのスタブ実装。
 */
@RestController
@RequestMapping("/api/v1/internal/coupons")
public class InternalCouponController {

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<Map<String, Object>>> findActiveCouponsForUser(@PathVariable String userId) {
        // TODO: UserCouponRepository.findActiveByUserId と Coupon を join して返却。現状空配列。
        return ResponseEntity.ok(List.of());
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/by-code/{code}")
    public ResponseEntity<Map<String, Object>> findByCode(@PathVariable String code) {
        // TODO: CouponRepository.findByCode を呼び出す。現状ダミー。
        return ResponseEntity.ok(Map.of(
                "couponId", "dummy-" + code,
                "couponCode", code,
                "couponType", "PERCENTAGE",
                "discountRate", new BigDecimal("0.10"),
                "discountAmount", BigDecimal.ZERO,
                "minimumOrder", new BigDecimal("3000"),
                "applicableCategory", "ALL",
                "expiresAt", LocalDate.now().plusMonths(1).toString(),
                "isStackable", false,
                "usageLimit", 1));
    }
}
