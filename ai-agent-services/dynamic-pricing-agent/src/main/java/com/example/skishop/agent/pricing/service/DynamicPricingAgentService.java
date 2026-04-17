package com.example.skishop.agent.pricing.service;

import com.example.skishop.agent.common.dto.BulkPricingRequest;
import com.example.skishop.agent.common.dto.PricingRequest;
import com.example.skishop.agent.common.dto.PricingResult;
import com.example.skishop.agent.pricing.tool.DynamicPricingToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

public class DynamicPricingAgentService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキーショップの動的価格決定 AI エージェントです。
            以下の5段階チェーンを必ず順番通りに実行してください。
            Step1 getBasePrice → Step2 applyDemandAdjustment → Step3 applyWeatherAdjustment
            → Step4 applyInventoryAdjustment → Step5 applyCustomerTierDiscount
            最後に 150 文字以内で priceJustification を生成。
            """;

    private final ChatClient chatClient;
    private final DynamicPricingToolService toolService;

    public DynamicPricingAgentService(
            @Qualifier("pricingAgentChatClient") ChatClient chatClient,
            DynamicPricingToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    public PricingResult calculatePrice(PricingRequest request) {
        log.info("DynamicPricingAgent calculatePrice: productId={}, userId={}",
                request.productId(), request.userId());

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        商品ID: %s
                        ユーザーID: %s
                        顧客ティア: %s
                        リゾート: %s
                        数量: %d
                        Chain Workflow で最終価格を算出してください。
                        """.formatted(
                        request.productId(), request.userId(),
                        request.customerTier(),
                        request.resortLocation() != null ? request.resortLocation() : "未指定",
                        request.quantity()))
                .tools(toolService)
                .call()
                .entity(PricingResult.class);
    }

    public List<PricingResult> calculateBulkPrices(BulkPricingRequest request) {
        log.info("DynamicPricingAgent calculateBulkPrices: userId={}, items={}",
                request.userId(), request.items().size());
        return request.items().stream()
                .map(item -> calculatePrice(new PricingRequest(
                        item.productId(), request.userId(),
                        request.customerTier(), request.resortLocation(), item.quantity())))
                .toList();
    }
}
