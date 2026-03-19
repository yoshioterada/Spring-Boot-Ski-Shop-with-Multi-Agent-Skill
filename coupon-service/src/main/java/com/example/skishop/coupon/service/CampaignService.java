package com.example.skishop.coupon.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.coupon.dto.CampaignResponse;
import com.example.skishop.coupon.dto.CreateCampaignRequest;
import com.example.skishop.coupon.dto.UpdateCampaignRequest;
import com.example.skishop.coupon.event.CouponEventPayloads;
import com.example.skishop.coupon.model.Campaign;
import com.example.skishop.coupon.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;
    private final EventPublisher eventPublisher;

    public CampaignService(CampaignRepository campaignRepository,
                           EventPublisher eventPublisher) {
        this.campaignRepository = campaignRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessRuleViolationException("CMP-4221",
                    "Start date must be before end date");
        }

        Campaign campaign = new Campaign(
                request.name(),
                request.description(),
                request.campaignType(),
                request.startDate(),
                request.endDate(),
                request.maxCoupons()
        );
        campaign = campaignRepository.save(campaign);

        log.info("Created campaign: {}", campaign.getName());
        return toCampaignResponse(campaign);
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign",
                        campaignId.toString()));
        return toCampaignResponse(campaign);
    }

    @Transactional(readOnly = true)
    public Page<CampaignResponse> getCampaigns(Pageable pageable) {
        return campaignRepository.findAll(pageable).map(this::toCampaignResponse);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> getActiveCampaigns() {
        return campaignRepository.findByActiveTrue().stream()
                .map(this::toCampaignResponse)
                .toList();
    }

    @Transactional
    public CampaignResponse updateCampaign(UUID campaignId, UpdateCampaignRequest request) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign",
                        campaignId.toString()));

        if (campaign.isActive()) {
            throw new BusinessRuleViolationException("CMP-4092",
                    "Cannot update an active campaign");
        }

        campaign.setName(request.name());
        if (request.description() != null) {
            campaign.setDescription(request.description());
        }
        if (request.startDate() != null) {
            campaign.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            campaign.setEndDate(request.endDate());
        }
        if (request.startDate() != null && request.endDate() != null
                && request.startDate().isAfter(request.endDate())) {
            throw new BusinessRuleViolationException("CMP-4004",
                    "Start date must be before end date");
        }
        if (request.maxCoupons() != null) {
            campaign.setMaxCoupons(request.maxCoupons());
        }
        if (request.rules() != null) {
            campaign.setRules(request.rules());
        }

        campaign = campaignRepository.save(campaign);
        log.info("Updated campaign: {}", campaign.getName());
        return toCampaignResponse(campaign);
    }

    @Transactional
    public CampaignResponse activateCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign",
                        campaignId.toString()));

        if (campaign.isActive()) {
            throw new BusinessRuleViolationException("CMP-4222",
                    "Campaign is already active");
        }

        campaign.activate();
        campaignRepository.save(campaign);

        eventPublisher.publish(DomainEvent.create("campaign.activated", "coupon-service",
                new CouponEventPayloads.CampaignActivatedPayload(
                        campaign.getId().toString(), campaign.getName())));

        log.info("Activated campaign: {}", campaign.getName());
        return toCampaignResponse(campaign);
    }

    private CampaignResponse toCampaignResponse(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getCampaignType(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.isActive(),
                campaign.getMaxCoupons(),
                campaign.getGeneratedCoupons(),
                campaign.getRules(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
