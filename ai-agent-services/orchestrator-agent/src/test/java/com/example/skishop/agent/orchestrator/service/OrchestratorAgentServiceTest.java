package com.example.skishop.agent.orchestrator.service;

import com.example.skishop.agent.common.dto.CustomerIntentResult;
import com.example.skishop.agent.common.dto.ExtractedConstraints;
import com.example.skishop.agent.common.dto.IntentCategory;
import com.example.skishop.agent.orchestrator.client.UserManagementClient;
import com.example.skishop.agent.orchestrator.dto.OrchestratorRequest;
import com.example.skishop.agent.orchestrator.dto.OrchestratorResponse;
import com.example.skishop.agent.orchestrator.dto.QuoteSummary;
import com.example.skishop.agent.orchestrator.invoker.WorkerAgentInvoker;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrchestratorAgentServiceTest {

    private ChatClient chat;
    private UserManagementClient userClient;
    private WorkerAgentInvoker workerInvoker;
    private OrchestratorAgentService service;

    @BeforeEach
    void setUp() {
        chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        userClient = mock(UserManagementClient.class);
        workerInvoker = mock(WorkerAgentInvoker.class);
        service = new OrchestratorAgentService(chat, userClient, workerInvoker);

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
                        List.of("スキー板", "ウェア"), "INTERMEDIATE", 500,
                        List.of(new UserManagementClient.CouponSummary("c1", "WINTER10", "PERCENTAGE",
                                "PERCENTAGE", "10", "3000", "2026-12-31T00:00:00Z")),
                        List.of()));

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
                        null, "BEGINNER", 0, null, List.of("sales unavailable")));

        var req = new OrchestratorRequest("u2", "ボードを選んで",
                null, null, false);

        OrchestratorResponse result = service.orchestrate(req, null);
        assertThat(result.userId()).isEqualTo("u1");
    }

    @Test
    void extractIntentOnly_returns_customer_intent_result() {
        var constraints = new ExtractedConstraints(
                "志賀高原", null, null, 2, "INTERMEDIATE", 50000,
                false, true, List.of("スキー板"));
        var intent = new CustomerIntentResult(
                "u1", "s1",
                new IntentCategory.Purchase("スキー板"),
                constraints, null, "summary", 0.9, Instant.now());
        when(workerInvoker.invokeCustomerIntent(eq("u1"), anyString(), anyString()))
                .thenReturn(intent);

        var req = new OrchestratorRequest("u1", "志賀高原で滑りたい",
                "s1", null, false);
        CustomerIntentResult result = service.extractIntentOnly(req);

        assertThat(result).isNotNull();
        assertThat(result.constraints().destination()).isEqualTo("志賀高原");
    }

    @Test
    void extractIntentOnly_generates_session_id_when_blank() {
        var intent = new CustomerIntentResult(
                "u3", "auto",
                new IntentCategory.Advice("装備選び"),
                new ExtractedConstraints(null, null, null, null, null, null, false, false, List.of()),
                null, "ok", 0.8, Instant.now());
        when(workerInvoker.invokeCustomerIntent(eq("u3"), anyString(), anyString()))
                .thenReturn(intent);

        var req = new OrchestratorRequest("u3", "おすすめは？", null, null, false);
        CustomerIntentResult result = service.extractIntentOnly(req);
        assertThat(result.userId()).isEqualTo("u3");
    }
}
