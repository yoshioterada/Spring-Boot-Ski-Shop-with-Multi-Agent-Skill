package com.example.skishop.coupon.dto;

import com.example.skishop.coupon.model.Campaign.CampaignType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateCampaignRequest(
        @NotBlank(message = "キャンペーン名は必須です")
        @Size(max = 255)
        String name,

        String description,

        @NotNull(message = "キャンペーンタイプは必須です")
        CampaignType campaignType,

        @NotNull(message = "開始日は必須です")
        Instant startDate,

        @NotNull(message = "終了日は必須です")
        @Future(message = "終了日は将来の日付を指定してください")
        Instant endDate,

        Integer maxCoupons
) {}
