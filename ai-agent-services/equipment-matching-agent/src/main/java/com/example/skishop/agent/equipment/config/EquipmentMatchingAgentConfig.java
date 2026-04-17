package com.example.skishop.agent.equipment.config;

import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EquipmentMatchingAgentConfig {

    @Bean("equipmentAgentChatClient")
    public ChatClient equipmentAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean("equipmentAgentToolCallbacks")
    public ToolCallback[] equipmentAgentToolCallbacks(EquipmentMatchingToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
