package com.example.skishop.coupon.controller;

import com.example.skishop.common.security.SecurityUtils;
import com.example.skishop.coupon.dto.*;
import com.example.skishop.coupon.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.createCoupon(request));
    }

    @GetMapping("/{code}")
    public ResponseEntity<CouponResponse> getCouponByCode(@PathVariable String code) {
        return ResponseEntity.ok(couponService.getCouponByCode(code));
    }

    @GetMapping
    public ResponseEntity<Page<CouponResponse>> getCouponsByCampaign(
            @RequestParam UUID campaignId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(couponService.getCouponsByCampaign(campaignId, pageable));
    }

    @PostMapping("/validate")
    public ResponseEntity<CouponValidationResponse> validateCoupon(
            @Valid @RequestBody CouponValidationRequest request) {
        return ResponseEntity.ok(couponService.validateCoupon(request));
    }

    @PostMapping("/redeem")
    public ResponseEntity<Void> redeemCoupon(@Valid @RequestBody CouponRedemptionRequest request) {
        SecurityUtils.verifyOwnershipOrAdmin(request.userId());
        couponService.redeemCoupon(request);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/usage/{couponId}")
    public ResponseEntity<Page<CouponUsageResponse>> getCouponUsage(
            @PathVariable UUID couponId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(couponService.getCouponUsage(couponId, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/bulk-generate")
    public ResponseEntity<BulkGenerationResponse> bulkGenerateCoupons(
            @Valid @RequestBody BulkGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(couponService.bulkGenerateCoupons(request));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/user/available")
    public ResponseEntity<List<CouponResponse>> getUserAvailableCoupons(
            @RequestParam UUID userId) {
        return ResponseEntity.ok(couponService.getUserAvailableCoupons(userId));
    }
}
