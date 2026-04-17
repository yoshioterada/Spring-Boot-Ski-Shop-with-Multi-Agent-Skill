package com.example.skishop.agent.coupon.config;

import com.example.skishop.agent.coupon.tool.CouponOptimizationToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CouponOptimizationAgentConfig {

    @Bean("couponAgentChatClient")
    public ChatClient couponAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean("couponAgentToolCallbacks")
    public ToolCallback[] couponAgentToolCallbacks(CouponOptimizationToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
