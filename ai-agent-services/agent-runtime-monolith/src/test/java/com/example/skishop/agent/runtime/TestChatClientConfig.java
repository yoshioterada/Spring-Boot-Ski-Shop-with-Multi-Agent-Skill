package com.example.skishop.agent.runtime;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Spring AI Azure OpenAI 自動構成を除外しているため、各 Worker / Orchestrator が必要とする
 * {@link ChatClient.Builder} をモックで提供する。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestChatClientConfig {

    @Bean
    public ChatClient.Builder chatClientBuilder() {
        return mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
    }
}
