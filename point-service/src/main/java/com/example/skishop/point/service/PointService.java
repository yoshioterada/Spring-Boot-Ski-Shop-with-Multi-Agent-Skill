package com.example.skishop.point.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.point.dto.*;
import com.example.skishop.point.event.PointEventPayloads;
import com.example.skishop.point.model.PointTransaction;
import com.example.skishop.point.model.PointTransaction.TransactionType;
import com.example.skishop.point.model.TierDefinition;
import com.example.skishop.point.model.TierDefinition.TierLevel;
import com.example.skishop.point.model.UserTier;
import com.example.skishop.point.repository.PointTransactionRepository;
import com.example.skishop.point.repository.TierDefinitionRepository;
import com.example.skishop.point.repository.UserTierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    private final UserTierRepository userTierRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final TierDefinitionRepository tierDefinitionRepository;
    private final EventPublisher eventPublisher;

    public PointService(UserTierRepository userTierRepository,
                        PointTransactionRepository pointTransactionRepository,
                        TierDefinitionRepository tierDefinitionRepository,
                        EventPublisher eventPublisher) {
        this.userTierRepository = userTierRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.tierDefinitionRepository = tierDefinitionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PointTransactionResponse awardPoints(AwardPointsRequest request) {
        UserTier userTier = getOrCreateUserTier(request.userId());

        TierDefinition tierDef = tierDefinitionRepository.findByLevel(userTier.getCurrentTier())
                .orElseThrow(() -> new ResourceNotFoundException("TierDefinition",
                        userTier.getCurrentTier().name()));

        int multipliedPoints = (int) Math.ceil(request.amount() * tierDef.getPointMultiplier().doubleValue());
        userTier.addPoints(multipliedPoints);
        userTierRepository.save(userTier);

        PointTransaction tx = new PointTransaction(
                request.userId(),
                TransactionType.EARNED,
                multipliedPoints,
                userTier.getCurrentBalance(),
                request.reason(),
                request.referenceId()
        );
        if (request.expiryDays() != null && request.expiryDays() > 0) {
            tx.setExpiresAt(Instant.now().plus(request.expiryDays(), ChronoUnit.DAYS));
        }
        tx = pointTransactionRepository.save(tx);

        checkAndUpgradeTier(userTier);

        eventPublisher.publish(DomainEvent.create("points.awarded", "point-service",
                new PointEventPayloads.PointsAwardedPayload(
                        request.userId().toString(), multipliedPoints, tx.getId().toString())));

        log.info("Awarded {} points to user {} (base={}, multiplier={})",
                multipliedPoints, request.userId(), request.amount(), tierDef.getPointMultiplier());

        return toTransactionResponse(tx);
    }

    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(UUID userId) {
        UserTier userTier = userTierRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserTier",
                        userId.toString()));

        TierDefinition tierDef = tierDefinitionRepository.findByLevel(userTier.getCurrentTier())
                .orElseThrow(() -> new ResourceNotFoundException("TierDefinition",
                        userTier.getCurrentTier().name()));

        TierLevel nextTier = getNextTier(userTier.getCurrentTier());
        int pointsToNextTier = 0;
        if (nextTier != null) {
            TierDefinition nextDef = tierDefinitionRepository.findByLevel(nextTier).orElse(null);
            if (nextDef != null) {
                pointsToNextTier = Math.max(0, nextDef.getMinPoints() - userTier.getTotalEarned());
            }
        }

        int expiringPoints = pointTransactionRepository.sumExpiringPoints(
                userId, Instant.now().plus(30, ChronoUnit.DAYS));

        return new PointBalanceResponse(
                userId,
                userTier.getTotalEarned(),
                userTier.getTotalRedeemed(),
                userTier.getCurrentBalance(),
                expiringPoints,
                userTier.getCurrentTier(),
                tierDef.getTierName(),
                tierDef.getPointMultiplier().doubleValue(),
                nextTier,
                pointsToNextTier
        );
    }

    @Transactional(readOnly = true)
    public Page<PointTransactionResponse> getHistory(UUID userId, Pageable pageable) {
        return pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toTransactionResponse);
    }

    @Transactional
    public void redeemPoints(RedeemPointsRequest request) {
        UserTier userTier = userTierRepository.findByUserId(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("UserTier",
                        request.userId().toString()));

        if (userTier.getCurrentBalance() < request.pointsToRedeem()) {
            throw new BusinessRuleViolationException("PNT-4221",
                    "Insufficient points. Current balance: " + userTier.getCurrentBalance()
                            + ", requested: " + request.pointsToRedeem());
        }

        userTier.redeemPoints(request.pointsToRedeem());
        userTierRepository.save(userTier);

        PointTransaction tx = new PointTransaction(
                request.userId(),
                TransactionType.REDEEMED,
                request.pointsToRedeem(),
                userTier.getCurrentBalance(),
                request.redemptionType(),
                request.referenceId()
        );
        pointTransactionRepository.save(tx);

        eventPublisher.publish(DomainEvent.create("points.redeemed", "point-service",
                new PointEventPayloads.PointsRedeemedPayload(
                        request.userId().toString(), request.pointsToRedeem(), request.redemptionType())));

        log.info("Redeemed {} points for user {}", request.pointsToRedeem(), request.userId());
    }

    @Transactional
    public void transferPoints(TransferPointsRequest request) {
        if (request.fromUserId().equals(request.toUserId())) {
            throw new BusinessRuleViolationException("PNT-4224", "Cannot transfer points to the same user");
        }

        UserTier fromTier = userTierRepository.findByUserId(request.fromUserId())
                .orElseThrow(() -> new ResourceNotFoundException("UserTier",
                        request.fromUserId().toString()));

        if (fromTier.getCurrentBalance() < request.amount()) {
            throw new BusinessRuleViolationException("PNT-4223",
                    "Insufficient points for transfer. Balance: " + fromTier.getCurrentBalance());
        }

        UserTier toTier = getOrCreateUserTier(request.toUserId());

        fromTier.redeemPoints(request.amount());
        toTier.addPoints(request.amount());
        userTierRepository.save(fromTier);
        userTierRepository.save(toTier);

        String reason = request.reason() != null ? request.reason() : "Point transfer";

        PointTransaction outTx = new PointTransaction(
                request.fromUserId(),
                TransactionType.TRANSFERRED_OUT,
                request.amount(),
                fromTier.getCurrentBalance(),
                reason + " -> " + request.toUserId(),
                null
        );
        pointTransactionRepository.save(outTx);

        PointTransaction inTx = new PointTransaction(
                request.toUserId(),
                TransactionType.TRANSFERRED_IN,
                request.amount(),
                toTier.getCurrentBalance(),
                reason + " <- " + request.fromUserId(),
                null
        );
        pointTransactionRepository.save(inTx);

        eventPublisher.publish(DomainEvent.create("points.transferred", "point-service",
                new PointEventPayloads.PointsTransferredPayload(
                        request.fromUserId().toString(), request.toUserId().toString(), request.amount())));

        log.info("Transferred {} points from {} to {}", request.amount(), request.fromUserId(), request.toUserId());
    }

    @Transactional(readOnly = true)
    public UserTierResponse getUserTier(UUID userId) {
        UserTier userTier = userTierRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserTier",
                        userId.toString()));

        return toUserTierResponse(userTier);
    }

    @Transactional(readOnly = true)
    @Cacheable("tierDefinitions")
    public List<TierDefinition> getAllTierDefinitions() {
        return tierDefinitionRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "tierByLevel", key = "#level")
    public TierDefinition getTierByLevel(TierLevel level) {
        return tierDefinitionRepository.findByLevel(level)
                .orElseThrow(() -> new ResourceNotFoundException("TierDefinition", level.name()));
    }

    @Transactional(readOnly = true)
    public Page<PointTransactionResponse> getHistoryByDateRange(UUID userId, Instant from, Instant to, Pageable pageable) {
        return pointTransactionRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, from, to, pageable)
                .map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public List<PointTransactionResponse> getExpiringPoints(UUID userId) {
        Instant thirtyDaysFromNow = Instant.now().plus(30, ChronoUnit.DAYS);
        return pointTransactionRepository.findExpiringPoints(userId, thirtyDaysFromNow)
                .stream().map(this::toTransactionResponse).toList();
    }

    private static final int EXPIRED_POINTS_CHUNK_SIZE = 100;

    @Transactional
    public int processExpiredPoints() {
        Instant now = Instant.now();
        int totalExpired = 0;
        int page = 0;

        Page<PointTransaction> chunk;
        do {
            chunk = pointTransactionRepository.findAllExpiredTransactions(now, PageRequest.of(page, EXPIRED_POINTS_CHUNK_SIZE));
            if (chunk.isEmpty()) {
                break;
            }

            Map<UUID, UserTier> userTierCache = new HashMap<>();
            List<PointTransaction> toSave = new ArrayList<>();

            for (PointTransaction tx : chunk.getContent()) {
                UUID userId = tx.getUserId();
                UserTier userTier = userTierCache.computeIfAbsent(userId,
                        uid -> userTierRepository.findByUserId(uid)
                                .orElseGet(() -> new UserTier(uid, TierDefinition.TierLevel.BRONZE)));

                tx.setExpired(true);
                userTier.redeemPoints(tx.getPoints());
                toSave.add(tx);

                PointTransaction expiryTx = new PointTransaction(
                        userId, TransactionType.EXPIRED,
                        tx.getPoints(), userTier.getCurrentBalance(),
                        "Point expired from tx: " + tx.getId(), tx.getId().toString());
                toSave.add(expiryTx);
                totalExpired += tx.getPoints();
            }

            pointTransactionRepository.saveAll(toSave);
            userTierRepository.saveAll(userTierCache.values());

            page++;
        } while (chunk.hasNext());

        log.info("Processed {} expired points", totalExpired);
        return totalExpired;
    }

    private UserTier getOrCreateUserTier(UUID userId) {
        return userTierRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserTier newTier = new UserTier(userId, TierLevel.BRONZE);
                    return userTierRepository.save(newTier);
                });
    }

    private void checkAndUpgradeTier(UserTier userTier) {
        List<TierDefinition> allTiers = new java.util.ArrayList<>(tierDefinitionRepository.findAll());
        allTiers.sort((a, b) -> Integer.compare(b.getMinPoints(), a.getMinPoints()));

        for (TierDefinition tierDef : allTiers) {
            if (userTier.getTotalEarned() >= tierDef.getMinPoints()) {
                if (tierDef.getLevel().ordinal() > userTier.getCurrentTier().ordinal()) {
                    TierLevel oldTier = userTier.getCurrentTier();
                    userTier.setCurrentTier(tierDef.getLevel());
                    userTier.setTierUpgradedAt(Instant.now());
                    userTierRepository.save(userTier);

                    eventPublisher.publish(DomainEvent.create("tier.upgraded", "point-service",
                            new PointEventPayloads.TierUpgradedPayload(
                                    userTier.getUserId().toString(),
                                    oldTier.name(),
                                    tierDef.getLevel().name())));

                    log.info("User {} upgraded from {} to {}",
                            userTier.getUserId(), oldTier, tierDef.getLevel());
                }
                break;
            }
        }
    }

    private TierLevel getNextTier(TierLevel current) {
        return switch (current) {
            case BRONZE -> TierLevel.SILVER;
            case SILVER -> TierLevel.GOLD;
            case GOLD -> TierLevel.PLATINUM;
            case PLATINUM -> null;
        };
    }

    private PointTransactionResponse toTransactionResponse(PointTransaction tx) {
        return new PointTransactionResponse(
                tx.getId(),
                tx.getUserId(),
                tx.getType(),
                tx.getPoints(),
                tx.getBalanceAfter(),
                tx.getDescription(),
                tx.getReferenceId(),
                tx.getExpiresAt(),
                tx.getCreatedAt()
        );
    }

    private UserTierResponse toUserTierResponse(UserTier userTier) {
        TierDefinition tierDef = tierDefinitionRepository.findByLevel(userTier.getCurrentTier())
                .orElse(null);
        double multiplier = tierDef != null ? tierDef.getPointMultiplier().doubleValue() : 1.0;
        String tierName = tierDef != null ? tierDef.getTierName() : userTier.getCurrentTier().name();
        Map<String, Object> benefits = tierDef != null ? tierDef.getBenefits() : Map.of();

        TierLevel nextTier = getNextTier(userTier.getCurrentTier());
        int pointsToNextTier = 0;
        if (nextTier != null) {
            TierDefinition nextDef = tierDefinitionRepository.findByLevel(nextTier).orElse(null);
            if (nextDef != null) {
                pointsToNextTier = Math.max(0, nextDef.getMinPoints() - userTier.getTotalEarned());
            }
        }

        return new UserTierResponse(
                userTier.getId(),
                userTier.getUserId(),
                userTier.getCurrentTier(),
                tierName,
                userTier.getTotalEarned(),
                userTier.getCurrentBalance(),
                multiplier,
                benefits,
                nextTier,
                pointsToNextTier,
                userTier.getTierUpgradedAt(),
                userTier.getCreatedAt()
        );
    }
}
