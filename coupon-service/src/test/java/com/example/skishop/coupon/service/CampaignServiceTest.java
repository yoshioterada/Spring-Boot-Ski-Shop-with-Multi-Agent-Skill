package com.example.skishop.coupon.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.coupon.dto.CampaignResponse;
import com.example.skishop.coupon.dto.CreateCampaignRequest;
import com.example.skishop.coupon.dto.UpdateCampaignRequest;
import com.example.skishop.coupon.model.Campaign;
import com.example.skishop.coupon.model.Campaign.CampaignType;
import com.example.skishop.coupon.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private EventPublisher eventPublisher;

    private CampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new CampaignService(campaignRepository, eventPublisher);
    }

    @Nested
    @DisplayName("createCampaign - キャンペーン作成")
    class CreateCampaignTests {

        @Test
        @DisplayName("有効なリクエストでキャンペーンを作成できる")
        void should_createCampaign_when_validRequest() {
            // Arrange
            Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
            Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
            var request = new CreateCampaignRequest("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);
            when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

            // Act
            CampaignResponse response = campaignService.createCampaign(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("冬セール");
            assertThat(response.rules()).isEmpty();
            verify(campaignRepository).save(any(Campaign.class));
        }

        @Test
        @DisplayName("開始日が終了日より後の場合、BusinessRuleViolationExceptionがスローされる")
        void should_throwBusinessRuleViolation_when_startDateAfterEndDate() {
            // Arrange
            Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
            Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
            var request = new CreateCampaignRequest("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);

            // Act & Assert
            assertThatThrownBy(() -> campaignService.createCampaign(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Start date must be before end date");
        }
    }

    @Nested
    @DisplayName("getCampaign - キャンペーン取得")
    class GetCampaignTests {

        @Test
        @DisplayName("存在するキャンペーンIDで取得できる")
        void should_returnCampaign_when_campaignExists() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            Instant start = Instant.now();
            Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

            // Act
            CampaignResponse response = campaignService.getCampaign(campaignId);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("冬セール");
        }

        @Test
        @DisplayName("存在しないキャンペーンIDで取得した場合、ResourceNotFoundExceptionがスローされる")
        void should_throwResourceNotFound_when_campaignNotExists() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> campaignService.getCampaign(campaignId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateCampaign - キャンペーン更新")
    class UpdateCampaignTests {

        @Test
        @DisplayName("非アクティブなキャンペーンを更新できる")
        void should_updateCampaign_when_notActive() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
            Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);
            setCampaignId(campaign, campaignId);

            UpdateCampaignRequest request = new UpdateCampaignRequest(
                    "春セール", "春季限定セール", start, end, 200,
                    java.util.Map.of("minPurchase", 5000));

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

            // Act
            CampaignResponse response = campaignService.updateCampaign(campaignId, request);

            // Assert
            assertThat(response).isNotNull();
            verify(campaignRepository).save(any(Campaign.class));
        }

        @Test
        @DisplayName("アクティブなキャンペーンを更新しようとすると例外がスローされる")
        void should_throwException_when_updatingActiveCampaign() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            Instant start = Instant.now();
            Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);
            campaign.activate();

            UpdateCampaignRequest request = new UpdateCampaignRequest(
                    "春セール", null, null, null, null, null);

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

            // Act & Assert
            assertThatThrownBy(() -> campaignService.updateCampaign(campaignId, request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Cannot update an active campaign");
        }

        @Test
        @DisplayName("存在しないキャンペーン更新でResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_campaignNotExistsForUpdate() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            UpdateCampaignRequest request = new UpdateCampaignRequest(
                    "春セール", null, null, null, null, null);

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> campaignService.updateCampaign(campaignId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("activateCampaign - キャンペーンアクティベート")
    class ActivateCampaignTests {

        @Test
        @DisplayName("非アクティブなキャンペーンをアクティベートできる")
        void should_activateCampaign_when_notActive() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            Instant start = Instant.now();
            Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);
            setCampaignId(campaign, campaignId);
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

            // Act
            CampaignResponse response = campaignService.activateCampaign(campaignId);

            // Assert
            assertThat(response).isNotNull();
            verify(campaignRepository).save(any(Campaign.class));
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("既にアクティブなキャンペーンをアクティベートした場合、BusinessRuleViolationExceptionがスローされる")
        void should_throwBusinessRuleViolation_when_alreadyActive() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            Instant start = Instant.now();
            Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, start, end, 100);
            campaign.activate();
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

            // Act & Assert
            assertThatThrownBy(() -> campaignService.activateCampaign(campaignId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already active");
        }
    }

    @Nested
    @DisplayName("getCampaigns - キャンペーン一覧/検索")
    class ListCampaignTests {

        @Test
        @DisplayName("キャンペーン一覧をページネーション付きで取得できる")
        void should_returnPagedCampaigns_when_getCampaigns() {
            // Arrange
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, Instant.now(),
                    Instant.now().plus(30, ChronoUnit.DAYS), 100);
            Page<Campaign> page = new PageImpl<>(List.of(campaign));
            when(campaignRepository.findAll(any(PageRequest.class))).thenReturn(page);

            // Act
            Page<CampaignResponse> result = campaignService.getCampaigns(PageRequest.of(0, 20));

            // Assert
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("アクティブなキャンペーンのみ取得できる")
        void should_returnOnlyActiveCampaigns_when_getActiveCampaigns() {
            // Arrange
            Campaign campaign = new Campaign("冬セール", "冬季限定セール",
                    CampaignType.PERCENTAGE, Instant.now(),
                    Instant.now().plus(30, ChronoUnit.DAYS), 100);
            campaign.activate();
            when(campaignRepository.findByActiveTrue()).thenReturn(List.of(campaign));

            // Act
            List<CampaignResponse> result = campaignService.getActiveCampaigns();

            // Assert
            assertThat(result).hasSize(1);
        }
    }

    private void setCampaignId(Campaign campaign, UUID id) {
        try {
            Field idField = Campaign.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(campaign, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
