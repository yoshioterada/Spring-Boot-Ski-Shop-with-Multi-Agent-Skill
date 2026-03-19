package com.example.skishop.coupon.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.coupon.dto.*;
import com.example.skishop.coupon.model.Campaign;
import com.example.skishop.coupon.model.Campaign.CampaignType;
import com.example.skishop.coupon.model.Coupon;
import com.example.skishop.coupon.model.Coupon.CouponType;
import com.example.skishop.coupon.model.Coupon.DiscountType;
import com.example.skishop.coupon.model.CouponUsage;
import com.example.skishop.coupon.model.UserCoupon;
import com.example.skishop.coupon.repository.CampaignRepository;
import com.example.skishop.coupon.repository.CouponRepository;
import com.example.skishop.coupon.repository.CouponUsageRepository;
import com.example.skishop.coupon.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CouponUsageRepository couponUsageRepository;
    @Mock
    private UserCouponRepository userCouponRepository;
    @Mock
    private EventPublisher eventPublisher;

    private CouponService couponService;

    private Campaign campaign;
    private Coupon coupon;

    @BeforeEach
    void setUp() throws Exception {
        couponService = new CouponService(couponRepository, campaignRepository,
                couponUsageRepository, userCouponRepository, eventPublisher);

        campaign = new Campaign("Winter Sale", "Desc", CampaignType.PERCENTAGE,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS), 100);
        setId(campaign, UUID.randomUUID());

        coupon = new Coupon(campaign, "WINTER20", CouponType.PERCENTAGE,
                DiscountType.PERCENTAGE, BigDecimal.valueOf(20),
                BigDecimal.valueOf(5000), BigDecimal.valueOf(10000),
                10, Instant.now().plus(30, ChronoUnit.DAYS));
        setId(coupon, UUID.randomUUID());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        var field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Nested
    @DisplayName("createCoupon - クーポン作成")
    class CreateCouponTests {

        @Test
        @DisplayName("有効なリクエストでクーポン作成が成功する")
        void should_succeed_when_validCouponRequest() throws Exception {
            // Arrange
            UUID campaignId = campaign.getId();
            CreateCouponRequest request = new CreateCouponRequest(
                    campaignId, "SPRING10", CouponType.PERCENTAGE, DiscountType.PERCENTAGE,
                    BigDecimal.TEN, BigDecimal.valueOf(1000), null, 5,
                    Instant.now().plus(30, ChronoUnit.DAYS));

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(couponRepository.existsByCode("SPRING10")).thenReturn(false);
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
                Coupon c = inv.getArgument(0);
                setId(c, UUID.randomUUID());
                return c;
            });
            when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

            // Act
            CouponResponse response = couponService.createCoupon(request);

            // Assert
            assertThat(response.code()).isEqualTo("SPRING10");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("重複コードでクーポン作成時に例外がスローされる")
        void should_throwException_when_duplicateCode() {
            // Arrange
            UUID campaignId = campaign.getId();
            CreateCouponRequest request = new CreateCouponRequest(
                    campaignId, "WINTER20", CouponType.PERCENTAGE, DiscountType.PERCENTAGE,
                    BigDecimal.TEN, null, null, 1,
                    Instant.now().plus(30, ChronoUnit.DAYS));

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(couponRepository.existsByCode("WINTER20")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> couponService.createCoupon(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("WINTER20");
        }

        @Test
        @DisplayName("キャンペーンが見つからない場合ResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_campaignNotExists() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            CreateCouponRequest request = new CreateCouponRequest(
                    campaignId, "TEST10", CouponType.PERCENTAGE, DiscountType.PERCENTAGE,
                    BigDecimal.TEN, null, null, 1,
                    Instant.now().plus(30, ChronoUnit.DAYS));

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> couponService.createCoupon(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("validateCoupon - クーポン検証")
    class ValidateCouponTests {

        @Test
        @DisplayName("有効なクーポン検証で割引額が返される")
        void should_returnDiscount_when_validCoupon() {
            // Arrange
            CouponValidationRequest request = new CouponValidationRequest(
                    "WINTER20", BigDecimal.valueOf(15000), UUID.randomUUID());

            when(couponRepository.findByCode("WINTER20")).thenReturn(Optional.of(coupon));

            // Act
            CouponValidationResponse response = couponService.validateCoupon(request);

            // Assert
            assertThat(response.valid()).isTrue();
            assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("存在しないクーポン検証で無効が返される")
        void should_returnInvalid_when_couponNotFound() {
            // Arrange
            CouponValidationRequest request = new CouponValidationRequest(
                    "INVALID", BigDecimal.valueOf(10000), UUID.randomUUID());

            when(couponRepository.findByCode("INVALID")).thenReturn(Optional.empty());

            // Act
            CouponValidationResponse response = couponService.validateCoupon(request);

            // Assert
            assertThat(response.valid()).isFalse();
            assertThat(response.message()).contains("not found");
        }

        @Test
        @DisplayName("最低注文額未満のクーポン検証で無効が返される")
        void should_returnInvalid_when_belowMinimumAmount() {
            // Arrange
            CouponValidationRequest request = new CouponValidationRequest(
                    "WINTER20", BigDecimal.valueOf(1000), UUID.randomUUID());

            when(couponRepository.findByCode("WINTER20")).thenReturn(Optional.of(coupon));

            // Act
            CouponValidationResponse response = couponService.validateCoupon(request);

            // Assert
            assertThat(response.valid()).isFalse();
            assertThat(response.message()).contains("Minimum order amount");
        }
    }

    @Nested
    @DisplayName("redeemCoupon - クーポン使用")
    class RedeemCouponTests {

        @Test
        @DisplayName("有効なクーポン使用で使用回数がインクリメントされる")
        void should_incrementUsedCount_when_validRedemption() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            CouponRedemptionRequest request = new CouponRedemptionRequest(
                    "WINTER20", userId, orderId, BigDecimal.valueOf(10000));

            when(couponRepository.findByCode("WINTER20")).thenReturn(Optional.of(coupon));
            when(couponRepository.save(any(Coupon.class))).thenReturn(coupon);
            when(couponUsageRepository.save(any(CouponUsage.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            couponService.redeemCoupon(request);

            // Assert
            assertThat(coupon.getUsedCount()).isEqualTo(1);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("存在しないクーポンコードでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_couponCodeNotExists() {
            // Arrange
            CouponRedemptionRequest request = new CouponRedemptionRequest(
                    "UNKNOWN", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(10000));

            when(couponRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> couponService.redeemCoupon(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("最低注文額未満でクーポン使用時にBusinessRuleViolationExceptionがスローされる")
        void should_throwException_when_belowMinimumForRedemption() {
            // Arrange
            CouponRedemptionRequest request = new CouponRedemptionRequest(
                    "WINTER20", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(100));

            when(couponRepository.findByCode("WINTER20")).thenReturn(Optional.of(coupon));

            // Act & Assert
            assertThatThrownBy(() -> couponService.redeemCoupon(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("minimum");
        }
    }

    @Nested
    @DisplayName("bulkGenerateCoupons - 一括クーポン生成")
    class BulkGenerateTests {

        @Test
        @DisplayName("一括クーポン生成が成功する")
        void should_generateCoupons_when_validRequest() throws Exception {
            // Arrange
            BulkGenerationRequest request = new BulkGenerationRequest(
                    campaign.getId(), CouponType.PERCENTAGE, DiscountType.PERCENTAGE,
                    BigDecimal.TEN, BigDecimal.valueOf(1000), null, 1,
                    Instant.now().plus(30, ChronoUnit.DAYS), 3, "BULK");

            when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
            when(couponRepository.existsByCode(any())).thenReturn(false);
            when(couponRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

            // Act
            BulkGenerationResponse response = couponService.bulkGenerateCoupons(request);

            // Assert
            assertThat(response.requestedCount()).isEqualTo(3);
            assertThat(response.generatedCount()).isEqualTo(3);
            assertThat(response.codes()).hasSize(3);
            verify(couponRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("キャンペーン上限超過時に制限される")
        void should_limitGeneration_when_campaignMaxReached() throws Exception {
            // Arrange
            Campaign limitedCampaign = new Campaign("Limited", "Desc", CampaignType.PERCENTAGE,
                    Instant.now().minus(1, ChronoUnit.DAYS),
                    Instant.now().plus(30, ChronoUnit.DAYS), 2);
            setId(limitedCampaign, UUID.randomUUID());

            BulkGenerationRequest request = new BulkGenerationRequest(
                    limitedCampaign.getId(), CouponType.PERCENTAGE, DiscountType.PERCENTAGE,
                    BigDecimal.TEN, null, null, 1,
                    Instant.now().plus(30, ChronoUnit.DAYS), 5, "LMT");

            when(campaignRepository.findById(limitedCampaign.getId()))
                    .thenReturn(Optional.of(limitedCampaign));
            when(couponRepository.existsByCode(any())).thenReturn(false);
            when(couponRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(campaignRepository.save(any(Campaign.class))).thenReturn(limitedCampaign);

            // Act
            BulkGenerationResponse response = couponService.bulkGenerateCoupons(request);

            // Assert
            assertThat(response.requestedCount()).isEqualTo(5);
            assertThat(response.generatedCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getUserAvailableCoupons - ユーザー利用可能クーポン取得")
    class UserAvailableCouponsTests {

        @Test
        @DisplayName("ユーザーの利用可能なクーポンのみ返される")
        void should_returnAvailableCoupons_when_userHasCoupons() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UserCoupon userCoupon = new UserCoupon(userId, coupon);
            when(userCouponRepository.findByUserIdAndRedeemedFalse(userId))
                    .thenReturn(List.of(userCoupon));

            // Act
            List<CouponResponse> result = couponService.getUserAvailableCoupons(userId);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().code()).isEqualTo("WINTER20");
        }

        @Test
        @DisplayName("利用可能なクーポンがない場合空リストが返される")
        void should_returnEmpty_when_noCouponsAvailable() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userCouponRepository.findByUserIdAndRedeemedFalse(userId))
                    .thenReturn(List.of());

            // Act
            List<CouponResponse> result = couponService.getUserAvailableCoupons(userId);

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
