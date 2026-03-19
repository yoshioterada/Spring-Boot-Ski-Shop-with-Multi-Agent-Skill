-- Point Service Schema Migration V1

-- Tier definitions table
CREATE TABLE tier_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_level VARCHAR(20) NOT NULL UNIQUE,
    min_points INTEGER NOT NULL DEFAULT 0,
    point_multiplier NUMERIC(5,2) NOT NULL DEFAULT 1.0,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User tier tracking table
CREATE TABLE user_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    current_tier VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    current_balance INTEGER NOT NULL DEFAULT 0,
    total_earned INTEGER NOT NULL DEFAULT 0,
    total_redeemed INTEGER NOT NULL DEFAULT 0,
    tier_upgraded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_tier_level FOREIGN KEY (current_tier) REFERENCES tier_definitions(tier_level)
);

-- Point transactions table
CREATE TABLE point_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    points INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    description VARCHAR(500),
    reference_id VARCHAR(100),
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transaction_type CHECK (transaction_type IN ('EARNED', 'REDEEMED', 'EXPIRED', 'TRANSFERRED_IN', 'TRANSFERRED_OUT'))
);

-- Indexes
CREATE INDEX idx_user_tiers_user_id ON user_tiers(user_id);
CREATE INDEX idx_point_transactions_user_id ON point_transactions(user_id);
CREATE INDEX idx_point_transactions_created_at ON point_transactions(created_at);
CREATE INDEX idx_point_transactions_user_created ON point_transactions(user_id, created_at);
CREATE INDEX idx_point_transactions_expires_at ON point_transactions(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_point_transactions_reference ON point_transactions(reference_id) WHERE reference_id IS NOT NULL;

-- Timestamp update trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_tier_definitions_updated_at
    BEFORE UPDATE ON tier_definitions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_tiers_updated_at
    BEFORE UPDATE ON user_tiers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Default tier definitions
INSERT INTO tier_definitions (tier_level, min_points, point_multiplier, description)
VALUES
    ('BRONZE', 0, 1.0, 'Entry level tier'),
    ('SILVER', 10000, 1.25, 'Silver tier - 1.25x point multiplier'),
    ('GOLD', 25000, 1.5, 'Gold tier - 1.5x point multiplier'),
    ('PLATINUM', 50000, 2.0, 'Platinum tier - 2.0x point multiplier');
