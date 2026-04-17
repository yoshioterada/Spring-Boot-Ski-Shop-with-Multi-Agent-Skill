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

            推奨手順:
            1. 各商品カテゴリごとに searchInventoryCandidates を呼び出す
            2. filterByBodyMeasurements で体型に合わない製品を除外
            3. scoreByWeatherConditions で気象適合性を評価
            4. rankProducts で最終ランキングを生成
            5. 推奨理由を 300 文字以内の aiRecommendationSummary に
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

        List<CompletableFuture<List<ProductCandidate>>> futures = request.desiredCategories().stream()
                .map(category -> CompletableFuture.supplyAsync(() ->
                        toolService.searchInventoryCandidates(category, request.skillLevel(), request.budgetYen())))
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
        return """
                ユーザーID: %s
                スキルレベル: %s
                希望カテゴリ: %s
                予算: %s円
                行き先: %s
                在庫候補数: %d件
                最適な製品セットを推奨してください。
                """.formatted(
                request.userId(),
                request.skillLevel(),
                String.join("・", request.desiredCategories()),
                request.budgetYen() != null ? request.budgetYen() : "未指定",
                request.destination() != null ? request.destination() : "未指定",
                candidates.size());
    }
}
