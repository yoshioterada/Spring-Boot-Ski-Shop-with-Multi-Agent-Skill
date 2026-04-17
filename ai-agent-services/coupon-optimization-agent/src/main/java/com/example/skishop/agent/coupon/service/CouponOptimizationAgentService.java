package com.example.skishop.agent.coupon.service;

import com.example.skishop.agent.common.dto.CartItemPricing;
import com.example.skishop.agent.common.dto.CouponOptimizationRequest;
import com.example.skishop.agent.common.dto.CouponOptimizationResult;
import com.example.skishop.agent.coupon.tool.CouponOptimizationToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;

public class CouponOptimizationAgentService {

    private static final Logger log = LoggerFactory.getLogger(CouponOptimizationAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたはスキーショップのクーポン最適化 AI エージェントです。
            Evaluator-Optimizer パターンで最適クーポン戦略を決定してください。
            ステップ1 [Generator]: getEligibleCoupons
            ステップ2 [Evaluator]: evaluateCouponCombinations
            ステップ3 [Optimizer]: selectOptimalCombination
            ステップ4 [Option]: usePoints=true なら calculatePointsUsage
            最終 optimizationSummary を 200 文字以内の日本語で生成。
            """;

    private final ChatClient chatClient;
    private final CouponOptimizationToolService toolService;

    public CouponOptimizationAgentService(
            @Qualifier("couponAgentChatClient") ChatClient chatClient,
            CouponOptimizationToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    public CouponOptimizationResult optimize(CouponOptimizationRequest request) {
        log.info("CouponOptimizationAgent optimize: userId={}, orderId={}",
                request.userId(), request.orderId());

        BigDecimal cartTotal = request.cartItems().stream()
                .map(CartItemPricing::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String categories = request.cartItems().stream()
                .map(CartItemPricing::category)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        ユーザーID: %s
                        注文ID: %s
                        カート合計: %,d円
                        商品カテゴリ: %s
                        顧客ティア: %s
                        ポイント使用: %s
                        クーポンコード: %s
                        """.formatted(
                        request.userId(), request.orderId(), cartTotal.intValue(), categories,
                        request.customerTier(),
                        request.usePoints() ? "はい" : "いいえ",
                        request.couponCode() != null ? request.couponCode() : "なし"))
                .tools(toolService)
                .call()
                .entity(CouponOptimizationResult.class);
    }
}
