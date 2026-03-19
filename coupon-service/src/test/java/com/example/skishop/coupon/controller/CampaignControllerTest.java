package com.example.skishop.coupon.controller;

import com.example.skishop.coupon.config.SecurityConfig;
import com.example.skishop.coupon.dto.CampaignResponse;
import com.example.skishop.coupon.dto.CreateCampaignRequest;
import com.example.skishop.coupon.model.Campaign.CampaignType;
import com.example.skishop.coupon.service.CampaignService;
import com.example.skishop.coupon.service.CouponService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignController.class)
@Import(SecurityConfig.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CampaignService campaignService;

    @MockitoBean
    private CouponService couponService;

    private final UUID campaignId = UUID.randomUUID();

    private CampaignResponse createCampaignResponse() {
        return new CampaignResponse(
                campaignId, "新春セール", "新春限定キャンペーン",
                CampaignType.PERCENTAGE,
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS),
                true, 1000, 50, null,
                Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("POST /api/v1/campaigns")
    class CreateCampaign {

        @Test
        @DisplayName("ADMIN権限でキャンペーンを作成できる")
        @WithMockUser(roles = "ADMIN")
        void should_createCampaign_when_admin() throws Exception {
            // Arrange
            var request = new CreateCampaignRequest(
                    "新春セール", "新春限定キャンペーン", CampaignType.PERCENTAGE,
                    Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), 1000);
            when(campaignService.createCampaign(any(CreateCampaignRequest.class)))
                    .thenReturn(createCampaignResponse());

            // Act & Assert
            mockMvc.perform(post("/api/v1/campaigns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("新春セール"));
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Arrange
            var request = new CreateCampaignRequest(
                    "新春セール", "新春限定キャンペーン", CampaignType.PERCENTAGE,
                    Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), 1000);

            // Act & Assert
            mockMvc.perform(post("/api/v1/campaigns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("未認証ユーザーは403エラーとなる")
        void should_returnForbidden_when_notAuthenticated() throws Exception {
            // Arrange
            var request = new CreateCampaignRequest(
                    "新春セール", "新春限定キャンペーン", CampaignType.PERCENTAGE,
                    Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), 1000);

            // Act & Assert
            mockMvc.perform(post("/api/v1/campaigns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("バリデーションエラーの場合、400エラーとなる")
        @WithMockUser(roles = "ADMIN")
        void should_returnBadRequest_when_invalidRequest() throws Exception {
            // Arrange
            var request = new CreateCampaignRequest(
                    "", null, null, null, null, null);

            // Act & Assert
            mockMvc.perform(post("/api/v1/campaigns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/campaigns/{campaignId}")
    class GetCampaign {

        @Test
        @DisplayName("ADMIN権限でキャンペーンを取得できる")
        @WithMockUser(roles = "ADMIN")
        void should_returnCampaign_when_admin() throws Exception {
            // Arrange
            when(campaignService.getCampaign(campaignId)).thenReturn(createCampaignResponse());

            // Act & Assert
            mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("新春セール"));
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/campaigns")
    class GetCampaigns {

        @Test
        @DisplayName("ADMIN権限でキャンペーン一覧を取得できる")
        @WithMockUser(roles = "ADMIN")
        void should_returnCampaigns_when_admin() throws Exception {
            // Arrange
            Page<CampaignResponse> page = new PageImpl<>(List.of(createCampaignResponse()));
            when(campaignService.getCampaigns(any())).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/campaigns"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("新春セール"));
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/campaigns"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/campaigns/{id}/activate")
    class ActivateCampaign {

        @Test
        @DisplayName("ADMIN権限でキャンペーンを有効化できる")
        @WithMockUser(roles = "ADMIN")
        void should_activateCampaign_when_admin() throws Exception {
            // Arrange
            when(campaignService.activateCampaign(campaignId)).thenReturn(createCampaignResponse());

            // Act & Assert
            mockMvc.perform(post("/api/v1/campaigns/{id}/activate", campaignId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("一般ユーザーは403エラーとなる")
        @WithMockUser(roles = "USER")
        void should_returnForbidden_when_notAdmin() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/campaigns/{id}/activate", campaignId))
                    .andExpect(status().isForbidden());
        }
    }
}
