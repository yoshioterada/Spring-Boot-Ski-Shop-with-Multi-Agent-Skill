package com.example.skishop.agent.inventory.config;

import com.example.skishop.agent.inventory.tool.InventoryMonitoringToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InventoryMonitoringAgentConfig {

    @Bean("inventoryAgentChatClient")
    public ChatClient inventoryAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean("inventoryAgentToolCallbacks")
    public ToolCallback[] inventoryAgentToolCallbacks(InventoryMonitoringToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
