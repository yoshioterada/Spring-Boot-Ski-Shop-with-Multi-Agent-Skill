package com.example.skishop.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RedeemPointsRequest(
        @NotNull(message = "ユーザーIDは必須です")
        UUID userId,

        @Min(value = 1, message = "ポイントは1以上を指定してください")
        int pointsToRedeem,

        @NotNull(message = "リデンプションタイプは必須です")
        @Size(max = 50)
        String redemptionType,

        @Size(max = 100)
        String referenceId
) {}
