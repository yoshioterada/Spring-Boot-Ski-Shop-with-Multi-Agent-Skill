package com.example.skishop.agent.intent.service;

import java.util.Objects;

import com.example.skishop.agent.common.dto.CustomerIntentRequest;
import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.intent.tool.CustomerIntentToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

public class CustomerIntentAgentService {

        private static final Logger log = LoggerFactory.getLogger(CustomerIntentAgentService.class);

        private static final String SYSTEM_PROMPT = """
                        あなたはスキーショップの顧客インテント解析 AI エージェントです。
                        提供されているツールを必要に応じて呼び出し、顧客のリクエストから以下を抽出してください。

                        抽出すべき情報:
                        1. インテントカテゴリ (PURCHASE / RENTAL / ADVICE / SUPPORT)
                        2. 目的地・スキーリゾート名
                        3. 旅行日程（開始日・終了日）
                        4. グループ人数
                        5. スキルレベル
                        6. 予算（円）
                        7. 希望商品カテゴリ
                        8. 購入・レンタルの別

                        ツール呼び出し手順（**各ツールは 1 回のみ**）:
                        1. getUserPurchaseHistory でユーザー履歴を確認
                        2. validateAndFillConstraints で制約を整理
                        3. 200 文字以内の intentSummary を生成

                        **出力 JSON のスキーマ重要事項**:
                        primaryIntent フィールドは必ず以下のいずれかの形式で出力すること
                        （type プロパティでカテゴリを判別する）:
                          - {"type":"PURCHASE","productCategory":"スキー板"}
                          - {"type":"RENTAL","productCategory":"スキー板","durationDays":3}
                          - {"type":"ADVICE","topic":"装備選び"}
                          - {"type":"SUPPORT","issueType":"返品"}
                        "category" や "requestedProductCategories" のような独自プロパティは禁止。
                        必ず "type" を含めること。
                        """;

        private final ChatClient chatClient;
        private final CustomerIntentToolService intentToolService;

        public CustomerIntentAgentService(
                        @Qualifier("customerIntentAgentChatClient") ChatClient chatClient,
                        CustomerIntentToolService intentToolService) {
                this.chatClient = chatClient;
                this.intentToolService = intentToolService;
        }

        public CustomerIntentResult analyze(CustomerIntentRequest request) {
                Objects.requireNonNull(request, "request must not be null");
                String userMessage = request.userMessage() != null ? request.userMessage() : "";
                if (log.isInfoEnabled()) {
                        log.info("CustomerIntentAgent analyze: userId={}, msgLen={}",
                                        request.userId(), userMessage.length());
                }

                String userPrompt = Objects.requireNonNull(
                                "ユーザーID: %s%nメッセージ: %s".formatted(request.userId(), userMessage),
                                "userPrompt must not be null");
                CustomerIntentResult result = chatClient.prompt()
                                .system(SYSTEM_PROMPT)
                                .user(userPrompt)
                                .tools(intentToolService)
                                .call()
                                .entity(CustomerIntentResult.class);

                if (log.isInfoEnabled()) {
                        log.info("CustomerIntentAgent result: confidence={}",
                                        result == null ? null : result.confidenceScore());
                }
                return result;
        }
}
