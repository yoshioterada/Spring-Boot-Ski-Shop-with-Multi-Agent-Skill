package com.example.skishop.coupon.dto;

import com.example.skishop.coupon.model.Campaign.CampaignType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        String name,
        String description,
        CampaignType campaignType,
        Instant startDate,
        Instant endDate,
        boolean active,
        Integer maxCoupons,
        int generatedCoupons,
        Map<String, Object> rules,
        Instant createdAt,
        Instant updatedAt
) {}
