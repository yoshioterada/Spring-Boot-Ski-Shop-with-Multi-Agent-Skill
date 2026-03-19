package com.example.skishop.point.controller;

import com.example.skishop.point.config.SecurityConfig;
import com.example.skishop.point.dto.*;
import com.example.skishop.point.model.PointTransaction.TransactionType;
import com.example.skishop.point.model.TierDefinition;
import com.example.skishop.point.model.TierDefinition.TierLevel;
import com.example.skishop.point.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
@Import(SecurityConfig.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PointService pointService;

    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("POST /api/v1/points/award")
    class AwardPoints {

        @Test
        @DisplayName("ADMIN権限でポイントを付与できる")
        @WithMockUser(roles = "ADMIN")
        void should_awardPoints_when_authenticated() throws Exception {
            // Arrange
            var request = new AwardPointsRequest(userId, 100, "購入", "REF-001", 30);
            var response = new PointTransactionResponse(
                    UUID.randomUUID(), userId, TransactionType.EARNED, 100, 100,
                    "購入", "REF-001", Instant.now().plusSeconds(86400 * 30), Instant.now());
            when(pointService.awardPoints(any(AwardPointsRequest.class))).thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/api/v1/points/award")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.amount").value(100))
                    .andExpect(jsonPath("$.transactionType").value("EARNED"));
        }

        @Test
        @DisplayName("未認証ユーザーは403エラーとなる")
        void should_returnForbidden_when_notAuthenticated() throws Exception {
            // Arrange
            var request = new AwardPointsRequest(userId, 100, "購入", "REF-001", 30);

            // Act & Assert
            mockMvc.perform(post("/api/v1/points/award")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("バリデーションエラーの場合、400エラーとなる")
        @WithMockUser(roles = "ADMIN")
        void should_returnBadRequest_when_invalidRequest() throws Exception {
            // Arrange
            var request = new AwardPointsRequest(null, 0, null, null, null);

            // Act & Assert
            mockMvc.perform(post("/api/v1/points/award")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/points/balance/{userId}")
    class GetBalance {

        @Test
        @DisplayName("ADMIN権限でポイント残高を取得できる")
        @WithMockUser(roles = "ADMIN")
        void should_returnBalance_when_authenticated() throws Exception {
            // Arrange
            var response = new PointBalanceResponse(userId, 500, 100, 400, 50,
                    TierLevel.SILVER, "シルバー", 1.5, TierLevel.GOLD, 500);
            when(pointService.getBalance(userId)).thenReturn(response);

            // Act & Assert
            mockMvc.perform(get("/api/v1/points/balance/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentBalance").value(400))
                    .andExpect(jsonPath("$.tierLevel").value("SILVER"));
        }

        @Test
        @DisplayName("未認証ユーザーは403エラーとなる")
        void should_returnForbidden_when_notAuthenticated() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/points/balance/{userId}", userId))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/points/process-expired")
    class ProcessExpiredPoints {

        @Test
        @DisplayName("ADMIN権限で期限切れポイント処理が実行できる")
        @WithMockUser(roles = "ADMIN")
        void should_processExpiredPoints_when_admin() throws Exception {
            // Arrange
            when(pointService.processExpiredPoints()).thenReturn(150);

            // Act & Assert
            mockMvc.perform(post("/api/v1/points/process-expired"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiredPoints").value(150));
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/points/process-expired"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("未認証ユーザーは403エラーとなる")
        void should_returnForbidden_when_notAuthenticated() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/points/process-expired"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tiers/user/{userId}")
    class GetUserTier {

        @Test
        @DisplayName("ADMIN権限でティア情報を取得できる")
        @WithMockUser(roles = "ADMIN")
        void should_returnUserTier_when_authenticated() throws Exception {
            // Arrange
            var response = new UserTierResponse(
                    UUID.randomUUID(), userId, TierLevel.GOLD, "ゴールド",
                    1000, 800, 2.0, Map.of("freeShipping", true),
                    TierLevel.PLATINUM, 1000, Instant.now(), Instant.now());
            when(pointService.getUserTier(userId)).thenReturn(response);

            // Act & Assert
            mockMvc.perform(get("/api/v1/tiers/user/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tierLevel").value("GOLD"))
                    .andExpect(jsonPath("$.currentBalance").value(800));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tiers")
    class GetAllTiers {

        @Test
        @DisplayName("認証済みユーザーが全ティア定義を取得できる")
        @WithMockUser
        void should_returnAllTiers_when_authenticated() throws Exception {
            // Arrange
            when(pointService.getAllTierDefinitions()).thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/api/v1/tiers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
