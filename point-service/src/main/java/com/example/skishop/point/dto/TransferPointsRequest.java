package com.example.skishop.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransferPointsRequest(
        @NotNull(message = "送信元ユーザーIDは必須です")
        UUID fromUserId,

        @NotNull(message = "送信先ユーザーIDは必須です")
        UUID toUserId,

        @Min(value = 1, message = "ポイントは1以上を指定してください")
        int amount,

        @Size(max = 100)
        String reason
) {}
