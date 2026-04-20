package com.example.skishop.agent.common.dto;

import java.util.List;
import java.util.Map;

/**
 * 在庫監視 AI エージェントの分析結果。
 *
 * <ul>
 *   <li>{@code items} - 各商品の在庫状況（ツールで取得した事実 + AI が補強した alert / alternativeProductIds）</li>
 *   <li>{@code overallSummary} - 全体傾向の自然言語サマリ（管理者向け、200 文字程度）</li>
 *   <li>{@code recommendedAction} - 次に取るべき推奨アクション</li>
 *   <li>{@code productNames} - {@code items} と {@code alternativeProductIds} で参照される
 *       全商品 ID → 商品名のルックアップマップ（フロントでの可読表示用）</li>
 * </ul>
 */
public record InventoryAnalysisResult(
        List<InventoryStatus> items,
        String overallSummary,
        String recommendedAction,
        Map<String, String> productNames
) {
    /**
     * 後方互換用：productNames を空マップで初期化するコンストラクタ。
     */
    public InventoryAnalysisResult(
            List<InventoryStatus> items,
            String overallSummary,
            String recommendedAction) {
        this(items, overallSummary, recommendedAction, Map.of());
    }
}
