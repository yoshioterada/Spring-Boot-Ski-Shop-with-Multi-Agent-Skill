-- V3: Add rules JSONB column to campaigns and missing indexes

-- Add rules column to campaigns
ALTER TABLE campaigns ADD COLUMN rules JSONB DEFAULT '{}';

-- Missing indexes per design document
CREATE INDEX idx_campaigns_dates ON campaigns(start_date, end_date);
CREATE INDEX idx_coupons_campaign_active ON coupons(campaign_id, is_active);
CREATE INDEX idx_coupon_usage_used_at ON coupon_usage(used_at);
CREATE INDEX idx_user_coupons_assigned_at ON user_coupons(assigned_at);
