package com.example.skishop.agent.orchestrator.service;

import com.example.skishop.agent.orchestrator.client.UserManagementClient;
import com.example.skishop.agent.orchestrator.dto.OrchestratorRequest;
import com.example.skishop.agent.orchestrator.dto.OrchestratorResponse;
import com.example.skishop.agent.orchestrator.dto.QuoteSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrchestratorAgentServiceTest {

    private ChatClient chat;
    private UserManagementClient userClient;
    private OrchestratorAgentService service;

    @BeforeEach
    void setUp() {
        chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        userClient = mock(UserManagementClient.class);
        service = new OrchestratorAgentService(chat, userClient);

        var quote = new QuoteSummary("o1", List.of(),
                BigDecimal.valueOf(50000), BigDecimal.valueOf(3000),
                BigDecimal.valueOf(1000), BigDecimal.valueOf(46000),
                "r1", Instant.now().plusSeconds(900));
        var resp = new OrchestratorResponse("u1", "s1", quote,
                "intent", "weather", "equipment", "coupon",
                "推奨カート構築完了", Instant.now());
        when(chat.prompt().system(anyString()).user(anyString()).call().entity(OrchestratorResponse.class))
                .thenReturn(resp);
    }

    @Test
    void orchestrate_with_full_profile_and_coupon_and_points() {
        when(userClient.getUserProfile(anyString(), any()))
                .thenReturn(new UserManagementClient.UserProfile("u1", "Alice", "GOLD",
                        List.of("スキー板", "ウェア"), "INTERMEDIATE", 500));

        var req = new OrchestratorRequest("u1", "初心者向けスキー板を選んで",
                "s1", "WINTER10", true);

        OrchestratorResponse result = service.orchestrate(req, "jwt-token");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.quote().totalAmount()).isEqualByComparingTo("46000");
    }

    @Test
    void orchestrate_with_null_coupon_and_no_points_uses_else_branches() {
        when(userClient.getUserProfile(anyString(), any()))
                .thenReturn(new UserManagementClient.UserProfile("u2", "Bob", "BRONZE",
                        null, "BEGINNER", 0));

        var req = new OrchestratorRequest("u2", "ボードを選んで",
                null, null, false);

        OrchestratorResponse result = service.orchestrate(req, null);
        assertThat(result.userId()).isEqualTo("u1");
    }
}
