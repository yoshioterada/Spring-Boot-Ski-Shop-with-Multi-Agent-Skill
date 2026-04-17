package com.example.skishop.agent.pricing.config;

import com.example.skishop.agent.pricing.tool.DynamicPricingToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DynamicPricingAgentConfig {

    @Bean("pricingAgentChatClient")
    public ChatClient pricingAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean("pricingAgentToolCallbacks")
    public ToolCallback[] pricingAgentToolCallbacks(DynamicPricingToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
