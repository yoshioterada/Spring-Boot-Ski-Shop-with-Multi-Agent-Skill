package com.example.skishop.coupon.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.coupon.dto.*;
import com.example.skishop.coupon.event.CouponEventPayloads;
import com.example.skishop.coupon.model.Campaign;
import com.example.skishop.coupon.model.Coupon;
import com.example.skishop.coupon.model.CouponUsage;
import com.example.skishop.coupon.model.UserCoupon;
import com.example.skishop.coupon.repository.CampaignRepository;
import com.example.skishop.coupon.repository.CouponRepository;
import com.example.skishop.coupon.repository.CouponUsageRepository;
import com.example.skishop.coupon.repository.UserCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final CampaignRepository campaignRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final UserCouponRepository userCouponRepository;
    private final EventPublisher eventPublisher;

    public CouponService(CouponRepository couponRepository,
                         CampaignRepository campaignRepository,
                         CouponUsageRepository couponUsageRepository,
                         UserCouponRepository userCouponRepository,
                         EventPublisher eventPublisher) {
        this.couponRepository = couponRepository;
        this.campaignRepository = campaignRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.userCouponRepository = userCouponRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign",
                        request.campaignId().toString()));

        if (couponRepository.existsByCode(request.code())) {
            throw new BusinessRuleViolationException("CPN-4091",
                    "Coupon code already exists: " + request.code());
        }

        if (campaign.getMaxCoupons() != null
                && campaign.getGeneratedCoupons() >= campaign.getMaxCoupons()) {
            throw new BusinessRuleViolationException("CPN-4002",
                    "Campaign has reached maximum coupon limit");
        }

        Coupon coupon = new Coupon(
                campaign,
                request.code(),
                request.couponType(),
                request.discountType(),
                request.discountValue(),
                request.minimumAmount(),
                request.maximumDiscount(),
                request.usageLimit(),
                request.expiresAt()
        );
        coupon = couponRepository.save(coupon);
        campaign.incrementGeneratedCoupons();
        campaignRepository.save(campaign);

        eventPublisher.publish(DomainEvent.create("coupon.created", "coupon-service",
                new CouponEventPayloads.CouponCreatedPayload(
                        coupon.getId().toString(), campaign.getId().toString(), coupon.getCode())));

        log.info("Created coupon {} for campaign {}", coupon.getCode(), campaign.getName());
        return toCouponResponse(coupon);
    }

    @Transactional(readOnly = true)
    public CouponResponse getCouponByCode(String code) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", code));
        return toCouponResponse(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> getCouponsByCampaign(UUID campaignId, Pageable pageable) {
        return couponRepository.findByCampaignId(campaignId, pageable)
                .map(this::toCouponResponse);
    }

    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(CouponValidationRequest request) {
        Coupon coupon = couponRepository.findByCode(request.code()).orElse(null);

        if (coupon == null) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, request.cartAmount(),
                    "Coupon not found");
        }

        if (!coupon.isUsable()) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, request.cartAmount(),
                    "Coupon is expired or usage limit reached");
        }

        if (coupon.getMinimumAmount() != null
                && request.cartAmount().compareTo(coupon.getMinimumAmount()) < 0) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, request.cartAmount(),
                    "Minimum order amount not met. Required: " + coupon.getMinimumAmount());
        }

        BigDecimal discount = coupon.calculateDiscount(request.cartAmount());
        BigDecimal finalAmount = request.cartAmount().subtract(discount);

        eventPublisher.publish(DomainEvent.create("coupon.validated", "coupon-service",
                new CouponEventPayloads.CouponValidatedPayload(
                        coupon.getId().toString(), request.userId().toString(), true)));

        return new CouponValidationResponse(true, discount, finalAmount,
                "Coupon is valid and applicable");
    }

    @Transactional
    public void redeemCoupon(CouponRedemptionRequest request) {
        Coupon coupon = couponRepository.findByCode(request.code())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon",
                        request.code()));

        if (!coupon.isUsable()) {
            throw new BusinessRuleViolationException("CPN-4221",
                    "Coupon is not usable: expired or usage limit reached");
        }

        if (coupon.getMinimumAmount() != null
                && request.orderAmount().compareTo(coupon.getMinimumAmount()) < 0) {
            throw new BusinessRuleViolationException("CPN-4223",
                    "Order amount does not meet minimum requirement: " + coupon.getMinimumAmount());
        }

        BigDecimal discount = coupon.calculateDiscount(request.orderAmount());
        coupon.incrementUsedCount();
        couponRepository.save(coupon);

        CouponUsage usage = new CouponUsage(
                coupon, request.userId(), request.orderId(), discount, request.orderAmount());
        couponUsageRepository.save(usage);

        eventPublisher.publish(DomainEvent.create("coupon.redeemed", "coupon-service",
                new CouponEventPayloads.CouponRedeemedPayload(
                        coupon.getId().toString(),
                        request.userId().toString(),
                        request.orderId().toString(),
                        discount.toPlainString())));

        log.info("Coupon {} redeemed by user {} for order {}", request.code(), request.userId(), request.orderId());
    }

    @Transactional(readOnly = true)
    public Page<CouponUsageResponse> getCouponUsage(UUID couponId, Pageable pageable) {
        return couponUsageRepository.findByCouponId(couponId, pageable)
                .map(this::toCouponUsageResponse);
    }

    @Transactional
    public BulkGenerationResponse bulkGenerateCoupons(BulkGenerationRequest request) {
        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign",
                        request.campaignId().toString()));

        int remaining = request.count();
        if (campaign.getMaxCoupons() != null) {
            int available = campaign.getMaxCoupons() - campaign.getGeneratedCoupons();
            if (available <= 0) {
                throw new BusinessRuleViolationException("CPN-4002",
                        "Campaign has reached maximum coupon limit");
            }
            remaining = Math.min(remaining, available);
        }

        String prefix = request.prefix() != null ? request.prefix() : "CPN";
        Set<String> generatedCodes = new LinkedHashSet<>();
        List<Coupon> coupons = new ArrayList<>();

        while (generatedCodes.size() < remaining) {
            String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (generatedCodes.contains(code)) {
                continue;
            }
            if (couponRepository.existsByCode(code)) {
                continue;
            }
            generatedCodes.add(code);

            Coupon coupon = new Coupon(
                    campaign, code, request.couponType(), request.discountType(),
                    request.discountValue(), request.minimumAmount(),
                    request.maximumDiscount(), request.usageLimit(), request.expiresAt());
            coupons.add(coupon);
        }

        couponRepository.saveAll(coupons);
        for (int i = 0; i < coupons.size(); i++) {
            campaign.incrementGeneratedCoupons();
        }
        campaignRepository.save(campaign);

        List<String> codeList = new ArrayList<>(generatedCodes);
        log.info("Bulk generated {} coupons for campaign {}", codeList.size(), campaign.getName());

        return new BulkGenerationResponse(request.count(), codeList.size(), codeList);
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> getUserAvailableCoupons(UUID userId) {
        return userCouponRepository.findByUserIdAndRedeemedFalse(userId).stream()
                .map(UserCoupon::getCoupon)
                .filter(Coupon::isUsable)
                .map(this::toCouponResponse)
                .toList();
    }

    private CouponUsageResponse toCouponUsageResponse(CouponUsage usage) {
        return new CouponUsageResponse(
                usage.getId(),
                usage.getCoupon().getId(),
                usage.getCoupon().getCode(),
                usage.getUserId(),
                usage.getOrderId(),
                usage.getDiscountApplied(),
                usage.getOrderAmount(),
                usage.getUsedAt()
        );
    }

    private CouponResponse toCouponResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCampaign().getId(),
                coupon.getCode(),
                coupon.getCouponType(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinimumAmount(),
                coupon.getMaximumDiscount(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                coupon.isActive(),
                coupon.getExpiresAt(),
                coupon.getCreatedAt()
        );
    }
}
