package com.example.skishop.agent.intent.service;

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

            ツール呼び出し手順:
            1. getUserPurchaseHistory でユーザー履歴を確認
            2. validateAndFillConstraints で制約を整理
            3. 200 文字以内の intentSummary を生成
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
        log.info("CustomerIntentAgent analyze: userId={}, msgLen={}",
                request.userId(), request.userMessage() == null ? 0 : request.userMessage().length());

        CustomerIntentResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("ユーザーID: %s%nメッセージ: %s".formatted(request.userId(), request.userMessage()))
                .tools(intentToolService)
                .call()
                .entity(CustomerIntentResult.class);

        log.info("CustomerIntentAgent result: confidence={}",
                result == null ? null : result.confidenceScore());
        return result;
    }
}
