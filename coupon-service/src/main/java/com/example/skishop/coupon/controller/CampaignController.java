package com.example.skishop.coupon.controller;

import com.example.skishop.coupon.dto.CampaignResponse;
import com.example.skishop.coupon.dto.CreateCampaignRequest;
import com.example.skishop.coupon.dto.UpdateCampaignRequest;
import com.example.skishop.coupon.service.CampaignService;
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
@RequestMapping("/api/v1/campaigns")
@PreAuthorize("hasRole('ADMIN')")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(request));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getCampaign(campaignId));
    }

    @PutMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody UpdateCampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(campaignId, request));
    }

    @GetMapping
    public ResponseEntity<Page<CampaignResponse>> getCampaigns(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignService.getCampaigns(pageable));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/active")
    public ResponseEntity<List<CampaignResponse>> getActiveCampaigns() {
        return ResponseEntity.ok(campaignService.getActiveCampaigns());
    }

    @PostMapping("/{campaignId}/activate")
    public ResponseEntity<CampaignResponse> activateCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.activateCampaign(campaignId));
    }
}
