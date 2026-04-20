package com.example.skishop.agent.common.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductCandidate(
        String productId,
        String productName,
        String category,
        String brand,
        BigDecimal basePrice,
        boolean isAvailable,
        int stockQuantity,
        String skillLevelSuitability,
        String weatherSuitability,
        Map<String, String> attributes,
        List<String> tags
) {
    /**
     * tags が null の場合は空リストへ正規化する（Jackson デシリアライズ等で欠落時の防御）。
     */
    public ProductCandidate {
        if (tags == null) tags = List.of();
    }

    /**
     * 後方互換コンストラクタ（既存テスト・コード用）。tags は空リストで初期化される。
     */
    public ProductCandidate(String productId, String productName, String category, String brand,
                            BigDecimal basePrice, boolean isAvailable, int stockQuantity,
                            String skillLevelSuitability, String weatherSuitability,
                            Map<String, String> attributes) {
        this(productId, productName, category, brand, basePrice, isAvailable, stockQuantity,
                skillLevelSuitability, weatherSuitability, attributes, List.of());
    }
}
