package com.example.skishop.mailsend.controller;

import com.example.skishop.mailsend.config.SecurityConfig;
import com.example.skishop.mailsend.dto.MailLogResponse;
import com.example.skishop.mailsend.dto.MailStatsResponse;
import com.example.skishop.mailsend.dto.TestMailRequest;
import com.example.skishop.mailsend.service.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MailController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-testing-only-minimum-256-bits-required-here-padding")
class MailControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private MailService mailService;

    @Test
    @DisplayName("ADMIN は送信ログ一覧を取得できる")
    @WithMockUser(roles = "ADMIN")
    void should_listLogs_when_admin() throws Exception {
        // Arrange
        var log = new MailLogResponse(UUID.randomUUID(), "TEST", "test@example.com", null,
                "welcome", "sub", "SENT", 0, null, Instant.now(), Instant.now());
        when(mailService.list(any())).thenReturn(new PageImpl<>(List.of(log)));

        // Act & Assert
        mockMvc.perform(get("/api/v1/mail/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recipientEmail").value("test@example.com"));
    }

    @Test
    @DisplayName("MANAGER は stats を取得できる")
    @WithMockUser(roles = "MANAGER")
    void should_getStats_when_manager() throws Exception {
        // Arrange
        when(mailService.stats()).thenReturn(new MailStatsResponse(1, 0, 0, 1.0, Map.of("welcome", 1L)));

        // Act & Assert
        mockMvc.perform(get("/api/v1/mail/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSent").value(1));
    }

    @Test
    @DisplayName("ADMIN は test mail を送信できる")
    @WithMockUser(roles = "ADMIN")
    void should_sendTestMail_when_admin() throws Exception {
        // Arrange
        var response = new MailLogResponse(UUID.randomUUID(), "TEST", "test@example.com", null,
                "welcome", "sub", "SENT", 0, null, Instant.now(), Instant.now());
        when(mailService.sendTestMail(any())).thenReturn(response);

        var request = new TestMailRequest("test@example.com", "welcome", Map.of("firstName", "太郎"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/mail/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateName").value("welcome"));
    }

    @Test
    @DisplayName("未認証は /logs にアクセスできない")
    void should_forbid_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/mail/logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN はIDを指定して送信ログを取得できる")
    @WithMockUser(roles = "ADMIN")
    void should_returnLog_when_adminGetsById() throws Exception {
        // Arrange
        var id = UUID.randomUUID();
        var log = new MailLogResponse(id, "ORDER_CONFIRMATION", "user@example.com", "山田太郎",
                "order-confirm", "ご注文確認", "SENT", 0, null, Instant.now(), Instant.now());
        when(mailService.get(eq(id))).thenReturn(log);

        // Act
        var result = mockMvc.perform(get("/api/v1/mail/logs/{id}", id));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.eventType").value("ORDER_CONFIRMATION"))
                .andExpect(jsonPath("$.recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.recipientName").value("山田太郎"))
                .andExpect(jsonPath("$.templateName").value("order-confirm"))
                .andExpect(jsonPath("$.subject").value("ご注文確認"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.retryCount").value(0));
    }

    @Test
    @DisplayName("ADMIN はメール送信をリトライできる")
    @WithMockUser(roles = "ADMIN")
    void should_retryMail_when_adminRetries() throws Exception {
        // Arrange
        var id = UUID.randomUUID();
        var retried = new MailLogResponse(id, "ORDER_CONFIRMATION", "user@example.com", "山田太郎",
                "order-confirm", "ご注文確認", "SENT", 1, null, Instant.now(), Instant.now());
        when(mailService.retry(eq(id))).thenReturn(retried);

        // Act
        var result = mockMvc.perform(post("/api/v1/mail/logs/{id}/retry", id));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.retryCount").value(1));
    }

    @Test
    @DisplayName("USER ロールは /logs にアクセスできない")
    @WithMockUser(roles = "USER")
    void should_reject_when_userRoleAccessesLogs() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/mail/logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER ロールはリトライできない")
    @WithMockUser(roles = "USER")
    void should_reject_when_userRoleAccessesRetry() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/mail/logs/{id}/retry", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MANAGER ロールはリトライできない（ADMIN のみ許可）")
    @WithMockUser(roles = "MANAGER")
    void should_reject_when_managerAccessesRetry() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/mail/logs/{id}/retry", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("テストメール送信時にバリデーションエラーが発生する（空ボディ）")
    @WithMockUser(roles = "ADMIN")
    void should_returnBadRequest_when_testMailMissingFields() throws Exception {
        // Arrange
        var emptyBody = "{}";

        // Act
        var result = mockMvc.perform(post("/api/v1/mail/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyBody));

        // Assert
        result.andExpect(status().isBadRequest());
    }
}
