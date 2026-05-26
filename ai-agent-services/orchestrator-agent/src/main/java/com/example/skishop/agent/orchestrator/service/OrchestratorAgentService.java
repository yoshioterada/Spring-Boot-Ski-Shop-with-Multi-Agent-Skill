package com.example.skishop.agent.orchestrator.service;

import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.orchestrator.client.UserManagementClient;
import com.example.skishop.agent.orchestrator.dto.OrchestratorRequest;
import com.example.skishop.agent.orchestrator.dto.OrchestratorResponse;
import com.example.skishop.agent.orchestrator.invoker.WorkerAgentInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Objects;
import java.util.UUID;

public class OrchestratorAgentService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgentService.class);

    private static final String ORCHESTRATOR_SYSTEM_PROMPT = """
            あなたはスキーショップの購入最適化オーケストレーター AI エージェントです。
            次の **必須ステップ** を厳密に 1 回ずつ実行し、最終 JSON を返してください。
            余計な思考や追加の確認・再試行は一切禁止します。

            必須ステップ（この順序で 1 回ずつ実行）:
              1. analyzeCustomerIntent
              2. getWeatherAndSkiConditions（行き先 1 箇所のみ）
              3. matchEquipment（必要な categories をまとめて 1 回）
              4. checkInventoryAvailability（matchEquipment の結果を 1 回でまとめて確認）
              5. calculateDynamicPrices
              6. optimizeCoupons
              7. buildCart
              8. reserveInventory

            **絶対に守る制約**:
            - 同じツールを 2 回以上呼び出してはならない（言い換え・別表記での再呼び出しも禁止）。
            - 最初のツール呼び出しの結果が空でも、追加のツール呼び出しは行わずに次のステップへ進む。
            - 上記 8 ステップを終えたら直ちに最終 JSON を返す。それ以上ツールを呼び出してはならない。
            - 最終出力に orchestrationSummary（日本語 300 文字以内）を必ず含める。
            - 予算超過時は優先度の高い商品から順に選択する。
            """;

    private final ChatClient orchestratorChatClient;
    private final UserManagementClient userManagementClient;
    private final WorkerAgentInvoker workerInvoker;

    public OrchestratorAgentService(
            @Qualifier("orchestratorChatClient") ChatClient orchestratorChatClient,
            UserManagementClient userManagementClient,
            WorkerAgentInvoker workerInvoker) {
        this.orchestratorChatClient = orchestratorChatClient;
        this.userManagementClient = userManagementClient;
        this.workerInvoker = workerInvoker;
    }

    /**
     * 軽量 Intent 抽出エンドポイント。
     * CustomerIntent のみを 1 回呼び出し、行き先・スキルレベル等を返す。
     * フルオーケストレーション (orchestrate) より大幅に高速。
     */
    public CustomerIntentResult extractIntentOnly(OrchestratorRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String userId = request.userId();
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();

        if (log.isInfoEnabled()) {
            log.info("intentOnly start: userId={}, sessionId={}", userId, sessionId);
        }

        CustomerIntentResult result = workerInvoker.invokeCustomerIntent(
                userId, request.message(), sessionId);

        if (log.isInfoEnabled()) {
            String destination = result != null && result.constraints() != null
                    ? result.constraints().destination()
                    : null;
            log.info("intentOnly done: destination={}", destination);
        }

        return result;
    }

    public OrchestratorResponse orchestrate(OrchestratorRequest request, String jwtToken) {
        Objects.requireNonNull(request, "request must not be null");
        String userId = request.userId();
        String orderId = UUID.randomUUID().toString();
        if (log.isInfoEnabled()) {
            log.info("Orchestrator start: userId={}, orderId={}", userId, orderId);
        }
        var userProfile = userManagementClient.getUserProfile(userId, jwtToken);
        if (log.isInfoEnabled()) {
            String customerTier = userProfile.customerTier();
            log.info("Orchestrator: userProfile fetched, tier={}", customerTier);
        }

        String userPrompt = Objects.requireNonNull(
                buildUserPrompt(request, orderId, userProfile),
                "userPrompt must not be null");
        OrchestratorResponse response = orchestratorChatClient.prompt()
                .system(ORCHESTRATOR_SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .entity(OrchestratorResponse.class);

        if (log.isInfoEnabled()) {
            log.info("Orchestrator completed: orderId={}", orderId);
        }

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
