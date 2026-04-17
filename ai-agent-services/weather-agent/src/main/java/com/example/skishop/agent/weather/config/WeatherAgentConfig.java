package com.example.skishop.agent.weather.config;

import com.example.skishop.agent.weather.tool.WeatherToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Weather Agent の Bean 定義。
 * <ul>
 *   <li>{@code weatherAgentChatClient}: ChatClient（Agent 名プレフィックスで衝突回避）</li>
 *   <li>{@code weatherAgentToolCallbacks}: Orchestrator が一括登録できる ToolCallback[]</li>
 * </ul>
 */
@Configuration
public class WeatherAgentConfig {

    @Bean("weatherAgentChatClient")
    public ChatClient weatherAgentChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    @Bean("weatherAgentToolCallbacks")
    public ToolCallback[] weatherAgentToolCallbacks(WeatherToolService weatherToolService) {
        return ToolCallbacks.from(weatherToolService);
    }
}
