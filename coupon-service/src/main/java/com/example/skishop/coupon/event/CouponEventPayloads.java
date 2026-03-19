package com.example.skishop.coupon.event;

public final class CouponEventPayloads {

    private CouponEventPayloads() {}

    public record CouponCreatedPayload(String couponId, String campaignId, String code) {}

    public record CouponValidatedPayload(String couponId, String userId, boolean isValid) {}

    public record CouponRedeemedPayload(String couponId, String userId, String orderId, String discountApplied) {}

    public record CampaignActivatedPayload(String campaignId, String name) {}

    public record CampaignCompletedPayload(String campaignId, int totalCoupons, int totalUsage, String reason) {}
}
