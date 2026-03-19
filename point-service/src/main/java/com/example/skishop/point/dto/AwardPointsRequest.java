package com.example.skishop.point.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AwardPointsRequest(
        @NotNull(message = "ユーザーIDは必須です")
        UUID userId,

        @Min(value = 1, message = "ポイントは1以上を指定してください")
        @Max(value = 100000, message = "ポイントは100000以下を指定してください")
        int amount,

        @NotNull(message = "理由は必須です")
        @Size(max = 100, message = "理由は100文字以内で入力してください")
        String reason,

        @Size(max = 100, message = "参照IDは100文字以内で入力してください")
        String referenceId,

        Integer expiryDays
) {}
