package com.example.skishop.agent.intent.config;

import com.example.skishop.agent.intent.tool.CustomerIntentToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomerIntentAgentConfig {

    @Bean("customerIntentAgentChatClient")
    public ChatClient customerIntentAgentChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean("customerIntentAgentToolCallbacks")
    public ToolCallback[] customerIntentAgentToolCallbacks(CustomerIntentToolService toolService) {
        return ToolCallbacks.from(toolService);
    }
}
