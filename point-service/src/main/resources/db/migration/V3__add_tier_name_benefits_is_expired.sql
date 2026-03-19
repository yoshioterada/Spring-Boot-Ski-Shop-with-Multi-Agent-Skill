-- Add tier_name, benefits JSONB to tier_definitions; add is_expired to point_transactions

ALTER TABLE tier_definitions ADD COLUMN tier_name VARCHAR(50);
UPDATE tier_definitions SET tier_name = CASE tier_level
    WHEN 'BRONZE' THEN 'ブロンズ'
    WHEN 'SILVER' THEN 'シルバー'
    WHEN 'GOLD' THEN 'ゴールド'
    WHEN 'PLATINUM' THEN 'プラチナ'
END;

ALTER TABLE tier_definitions ADD COLUMN benefits JSONB DEFAULT '{}';

ALTER TABLE point_transactions ADD COLUMN is_expired BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_point_transactions_is_expired ON point_transactions(expires_at) WHERE is_expired = false AND expires_at IS NOT NULL;
