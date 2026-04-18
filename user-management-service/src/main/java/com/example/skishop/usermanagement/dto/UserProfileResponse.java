package com.example.skishop.usermanagement.dto;

import java.util.List;
import java.util.UUID;

/**
 * Multi-Agent Orchestrator が利用する顧客プロファイル。
 * 既存の {@link UserResponse} に、エージェント推薦に必要な
 * customerTier/purchasedCategories/preferredSkillLevel/pointBalance を追加した派生 DTO。
 *
 * <p>customerTier / pointBalance は本サービスでは保持していないため、
 * 段階的に point-service / sales-management-service との連携で実値を取得する想定。
 * 暫定的には STANDARD / 0 を返すスタブ実装。
 */
public record UserProfileResponse(
        UUID userId,
        String displayName,
        String customerTier,
        List<String> purchasedCategories,
        String preferredSkillLevel,
        long pointBalance
) {}
