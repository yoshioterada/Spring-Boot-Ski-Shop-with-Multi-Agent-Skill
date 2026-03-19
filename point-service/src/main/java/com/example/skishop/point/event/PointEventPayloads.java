package com.example.skishop.point.event;

public final class PointEventPayloads {

    private PointEventPayloads() {}

    public record PointsAwardedPayload(String userId, int points, String transactionId) {}

    public record PointsRedeemedPayload(String userId, int points, String redemptionType) {}

    public record PointsTransferredPayload(String fromUserId, String toUserId, int amount) {}

    public record TierUpgradedPayload(String userId, String oldTier, String newTier) {}
}
