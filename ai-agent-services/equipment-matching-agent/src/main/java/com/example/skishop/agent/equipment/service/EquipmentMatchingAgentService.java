package com.example.skishop.agent.equipment.service;

import com.example.skishop.agent.common.dto.EquipmentMatchRequest;
import com.example.skishop.agent.common.dto.EquipmentMatchResult;
import com.example.skishop.agent.common.dto.ProductCandidate;
import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EquipmentMatchingAgentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentMatchingAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキー用品の専門アドバイザー AI エージェントです。
            提供されているツールを使い、以下の手順で最適な製品を推奨してください。

            推奨手順（各ツールを **1 回のみ** 呼び出す）:
            1. filterByBodyMeasurements で体型に合わない製品を 1 度だけ除外
            2. rankProducts で最終ランキングを 1 度だけ生成
            3. 推奨理由を 300 文字以内の aiRecommendationSummary に

            **絶対に守る制約**:
            - searchInventoryCandidates は呼び出さない（既に上位で取得済み）。
            - scoreByWeatherConditions は呼び出さない（時間がかかるためスキップ）。
            - 各ツールは 1 回ずつのみ呼び出す。複数回の呼び出し・別パラメータでの再試行は禁止。
            - 結果が空・不十分でも追加呼び出しせず、その情報をもとに最終 JSON を返す。
            """;

    private final ChatClient chatClient;
    private final EquipmentMatchingToolService toolService;

    public EquipmentMatchingAgentService(
            @Qualifier("equipmentAgentChatClient") ChatClient chatClient,
            EquipmentMatchingToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    public EquipmentMatchResult match(EquipmentMatchRequest request) {
        log.info("EquipmentMatchingAgent match: userId={}, categories={}",
                request.userId(), request.desiredCategories());

        // 予算は複数カテゴリの合計に対する上限のため、カテゴリ単位の検索では適用しない。
        // 個別カテゴリの最低価格より総予算が小さいケースで結果が空になるのを避ける目的。
        // 総予算超過のチェックは LLM の rankProducts ステップで行う。
        List<CompletableFuture<List<ProductCandidate>>> futures = request.desiredCategories().stream()
                .map(category -> CompletableFuture.supplyAsync(() ->
                        toolService.searchInventoryCandidates(category, request.skillLevel(), null)))
                .toList();

        List<ProductCandidate> allCandidates = new ArrayList<>();
        futures.forEach(f -> allCandidates.addAll(f.join()));

        log.info("EquipmentMatchingAgent: {} candidates found", allCandidates.size());

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserPrompt(request, allCandidates))
                .tools(toolService)
                .call()
                .entity(EquipmentMatchResult.class);
    }

    static String buildUserPrompt(EquipmentMatchRequest request, List<ProductCandidate> candidates) {
        String candidateList = candidates.isEmpty()
                ? "（候補なし）"
                : candidates.stream()
                        .map(c -> "- productId=%s, name=%s, brand=%s, category=%s, basePrice=%s, stock=%d, skillLevel=%s, available=%s"
                                .formatted(c.productId(), c.productName(), c.brand(), c.category(),
                                        c.basePrice(), c.stockQuantity(), c.skillLevelSuitability(), c.isAvailable()))
                        .reduce((a, b) -> a + "\n" + b).orElse("");

        return """
                ユーザーID: %s
                スキルレベル: %s
                希望カテゴリ: %s
                予算（合計）: %s円
                行き先: %s
                在庫候補（%d件、必ずこのリストの productId のみを使うこと）:
                %s

                最適な製品セットを推奨してください。
                ※ rankProducts には上記の在庫候補リスト全件をそのまま candidates 引数として渡すこと。
                ※ 合計予算を超えないように組合せを選び、超える場合はその旨を aiRecommendationSummary に明記。
                """.formatted(
                request.userId(),
                request.skillLevel(),
                String.join("・", request.desiredCategories()),
                request.budgetYen() != null ? request.budgetYen() : "未指定",
                request.destination() != null ? request.destination() : "未指定",
                candidates.size(),
                candidateList);
    }
}
