package com.example.skishop.agent.orchestrator.config;

import java.util.Objects;

import com.example.skishop.agent.orchestrator.tool.OrchestratorWorkerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrchestratorAgentConfig {

    /**
     * Orchestrator 専用 ChatClient。Orchestrator が AI モデルに渡す唯一の ToolCallback[] を
     * defaultToolCallbacks 登録。
     * Worker モジュール側の {@code <short>AgentToolCallbacks} Bean は
     * Orchestrator では使用しない。
     */
    @Bean("orchestratorChatClient")
    public ChatClient orchestratorChatClient(
            ChatClient.Builder builder,
            @Qualifier("orchestratorWorkerToolCallbacks") ToolCallback[] orchestratorWorkerToolCallbacks) {
        Objects.requireNonNull(builder, "builder must not be null");
        ToolCallback[] toolCallbacks = Objects.requireNonNull(
                orchestratorWorkerToolCallbacks,
                "orchestratorWorkerToolCallbacks must not be null");

        return builder.defaultToolCallbacks(toolCallbacks).build();
    }

    @Bean("orchestratorWorkerToolCallbacks")
    public ToolCallback[] orchestratorWorkerToolCallbacks(OrchestratorWorkerTools tools) {
        Objects.requireNonNull(tools, "tools must not be null");

        return Objects.requireNonNull(
                ToolCallbacks.from(tools),
                "ToolCallbacks.from(tools) must not return null");
    }
}
