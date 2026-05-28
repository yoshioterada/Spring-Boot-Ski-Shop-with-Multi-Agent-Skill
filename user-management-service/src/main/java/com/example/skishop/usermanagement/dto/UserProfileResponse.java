package com.example.skishop.usermanagement.dto;

import java.util.List;
import java.util.UUID;

/**
 * Multi-Agent Orchestrator が利用する顧客プロファイル。
 * 既存の {@link UserResponse} に、エージェント推薦に必要な
 * customerTier/purchasedCategories/preferredSkillLevel/pointBalance を追加した集約 DTO。
 */
public record UserProfileResponse(
        UUID userId,
        String displayName,
        String customerTier,
        List<String> purchasedCategories,
        String preferredSkillLevel,
        long pointBalance,
        List<CouponSummary> availableCoupons,
        List<String> warnings
) {}
