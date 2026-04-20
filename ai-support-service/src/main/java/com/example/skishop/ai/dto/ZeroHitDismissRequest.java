package com.example.skishop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * F5 dismiss リクエスト (spec § 20.5.2).
 */
public record ZeroHitDismissRequest(
        @NotBlank String reason,
        @Size(max = 500) String memo
) {}
