package com.example.skishop.agent.orchestrator.service;

import com.example.skishop.agent.orchestrator.client.UserManagementClient;
import com.example.skishop.agent.orchestrator.dto.OrchestratorRequest;
import com.example.skishop.agent.orchestrator.dto.OrchestratorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.UUID;

public class OrchestratorAgentService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgentService.class);

    private static final String ORCHESTRATOR_SYSTEM_PROMPT = """
            あなたはスキーショップの購入最適化オーケストレーター AI エージェントです。
            以下の手順で各 Worker Agent を協調させ、最適な推奨カートを構築してください。

            Phase 1 - 意図解析: analyzeCustomerIntent
            Phase 2 - コンテキスト収集: getWeatherAndSkiConditions, matchEquipment
            Phase 3 - 在庫確認: checkInventoryAvailability
            Phase 4 - 価格・クーポン: calculateDynamicPrices, optimizeCoupons
            Phase 5 - カート構築・予約: buildCart, reserveInventory

            最終出力に orchestrationSummary（日本語300文字以内）を必ず含めること。
            予算超過時は優先度の高い商品から順に選択する。
            """;

    private final ChatClient orchestratorChatClient;
    private final UserManagementClient userManagementClient;

    public OrchestratorAgentService(
            @Qualifier("orchestratorChatClient") ChatClient orchestratorChatClient,
            UserManagementClient userManagementClient) {
        this.orchestratorChatClient = orchestratorChatClient;
        this.userManagementClient = userManagementClient;
    }

    public OrchestratorResponse orchestrate(OrchestratorRequest request, String jwtToken) {
        String orderId = UUID.randomUUID().toString();
        log.info("Orchestrator start: userId={}, orderId={}", request.userId(), orderId);

        var userProfile = userManagementClient.getUserProfile(request.userId(), jwtToken);
        log.info("Orchestrator: userProfile fetched, tier={}", userProfile.customerTier());

        OrchestratorResponse response = orchestratorChatClient.prompt()
                .system(ORCHESTRATOR_SYSTEM_PROMPT)
                .user(buildUserPrompt(request, orderId, userProfile))
                .call()
                .entity(OrchestratorResponse.class);

        log.info("Orchestrator completed: orderId={}", orderId);
        return response;
    }

    private String buildUserPrompt(OrchestratorRequest request, String orderId,
                                    UserManagementClient.UserProfile profile) {
        return """
                ユーザーID: %s
                注文ID: %s
                顧客ティア: %s
                過去購入カテゴリ: %s
                リクエスト: %s
                クーポンコード: %s
                ポイント使用希望: %s
                """.formatted(
                request.userId(), orderId, profile.customerTier(),
                profile.purchasedCategories() == null ? "" : String.join("・", profile.purchasedCategories()),
                request.message(),
                request.couponCode() != null ? request.couponCode() : "なし",
                request.usePoints() ? "はい" : "いいえ");
    }
}
