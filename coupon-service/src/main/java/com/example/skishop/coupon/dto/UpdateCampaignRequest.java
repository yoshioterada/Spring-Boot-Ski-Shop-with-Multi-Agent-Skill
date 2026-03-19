package com.example.skishop.coupon.dto;

import com.example.skishop.coupon.model.Campaign.CampaignType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record UpdateCampaignRequest(
        @NotBlank(message = "キャンペーン名は必須です")
        @Size(max = 255)
        String name,

        String description,

        Instant startDate,

        Instant endDate,

        Integer maxCoupons,

        Map<String, Object> rules
) {}
