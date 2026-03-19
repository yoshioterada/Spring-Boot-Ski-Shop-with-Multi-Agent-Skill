package com.example.skishop.point.controller;

import com.example.skishop.common.security.SecurityUtils;
import com.example.skishop.point.dto.*;
import com.example.skishop.point.model.TierDefinition;
import com.example.skishop.point.model.TierDefinition.TierLevel;
import com.example.skishop.point.service.PointService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/points/award")
    public ResponseEntity<PointTransactionResponse> awardPoints(@Valid @RequestBody AwardPointsRequest request) {
        PointTransactionResponse response = pointService.awardPoints(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/points/balance/{userId}")
    public ResponseEntity<PointBalanceResponse> getBalance(@PathVariable UUID userId) {
        return ResponseEntity.ok(pointService.getBalance(userId));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/points/history/{userId}")
    public ResponseEntity<Page<PointTransactionResponse>> getHistory(
            @PathVariable UUID userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pointService.getHistory(userId, pageable));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/points/history/{userId}/range")
    public ResponseEntity<Page<PointTransactionResponse>> getHistoryByDateRange(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pointService.getHistoryByDateRange(userId, from, to, pageable));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/points/expiring/{userId}")
    public ResponseEntity<List<PointTransactionResponse>> getExpiringPoints(@PathVariable UUID userId) {
        return ResponseEntity.ok(pointService.getExpiringPoints(userId));
    }

    @PostMapping("/points/redeem")
    public ResponseEntity<Void> redeemPoints(@Valid @RequestBody RedeemPointsRequest request) {
        SecurityUtils.verifyOwnershipOrAdmin(request.userId());
        pointService.redeemPoints(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/points/transfer")
    public ResponseEntity<Void> transferPoints(@Valid @RequestBody TransferPointsRequest request) {
        SecurityUtils.verifyOwnershipOrAdmin(request.fromUserId());
        pointService.transferPoints(request);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/points/process-expired")
    public ResponseEntity<Map<String, Integer>> processExpiredPoints() {
        int totalExpired = pointService.processExpiredPoints();
        return ResponseEntity.ok(Map.of("expiredPoints", totalExpired));
    }

    @PreAuthorize("#userId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/tiers/user/{userId}")
    public ResponseEntity<UserTierResponse> getUserTier(@PathVariable UUID userId) {
        return ResponseEntity.ok(pointService.getUserTier(userId));
    }

    @GetMapping("/tiers")
    public ResponseEntity<List<TierDefinition>> getAllTiers() {
        return ResponseEntity.ok(pointService.getAllTierDefinitions());
    }

    @GetMapping("/tiers/{tierLevel}")
    public ResponseEntity<TierDefinition> getTierByLevel(@PathVariable TierLevel tierLevel) {
        return ResponseEntity.ok(pointService.getTierByLevel(tierLevel));
    }
}
