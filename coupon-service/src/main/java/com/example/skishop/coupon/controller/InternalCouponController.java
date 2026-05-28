package com.example.skishop.coupon.controller;

import com.example.skishop.coupon.model.Coupon;
import com.example.skishop.coupon.repository.CouponRepository;
import com.example.skishop.coupon.repository.UserCouponRepository;
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
import java.util.UUID;

/**
 * Multi-Agent Worker (CouponServiceClient) 向け内部 API。
 */
@RestController
@RequestMapping("/api/v1/internal/coupons")
public class InternalCouponController {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;

    public InternalCouponController(UserCouponRepository userCouponRepository,
                                    CouponRepository couponRepository) {
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<CouponProfileSummary>> findActiveCouponsForUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(findUsableCoupons(userId));
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/users/{userId}/summary")
    public ResponseEntity<List<CouponProfileSummary>> findCouponSummaryForUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(findUsableCoupons(userId));
    }

    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    @GetMapping("/by-code/{code}")
    public ResponseEntity<Map<String, Object>> findByCode(@PathVariable String code) {
        Coupon coupon = couponRepository.findByCode(code).orElse(null);
        if (coupon != null) {
            return ResponseEntity.ok(Map.of(
                    "couponId", coupon.getId().toString(),
                    "couponCode", coupon.getCode(),
                    "couponType", coupon.getCouponType().name(),
                    "discountType", coupon.getDiscountType().name(),
                    "discountValue", coupon.getDiscountValue(),
                    "minimumOrder", coupon.getMinimumAmount(),
                    "expiresAt", coupon.getExpiresAt().toString(),
                    "usageLimit", coupon.getUsageLimit(),
                    "usedCount", coupon.getUsedCount(),
                    "usable", coupon.isUsable()));
        }
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

    private List<CouponProfileSummary> findUsableCoupons(UUID userId) {
        return userCouponRepository.findByUserIdAndRedeemedFalse(userId).stream()
                .map(uc -> uc.getCoupon())
                .filter(Coupon::isUsable)
                .map(coupon -> new CouponProfileSummary(
                        coupon.getId().toString(),
                        coupon.getCode(),
                        coupon.getCouponType().name(),
                        coupon.getDiscountType().name(),
                        coupon.getDiscountValue().toPlainString(),
                        coupon.getMinimumAmount().toPlainString(),
                        coupon.getExpiresAt().toString()))
                .toList();
    }

    public record CouponProfileSummary(
            String couponId,
            String couponCode,
            String couponType,
            String discountType,
            String discountValue,
            String minimumAmount,
            String expiresAt
    ) {}
}
