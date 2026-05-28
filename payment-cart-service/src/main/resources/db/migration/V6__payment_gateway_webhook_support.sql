ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS ck_payments_status;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS gateway_payment_id VARCHAR(150),
    ADD COLUMN IF NOT EXISTS raw_gateway_status VARCHAR(100),
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS authorized_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS captured_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_status CHECK (
        status IN ('PENDING','REQUIRES_ACTION','AUTHORIZED','CAPTURED','FAILED','CANCELLED','PARTIALLY_REFUNDED','REFUNDED')
    );

CREATE INDEX IF NOT EXISTS idx_payments_gateway_payment_id ON payments(gateway_payment_id);

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    event_id VARCHAR(150) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payment_id UUID,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_message VARCHAR(500),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_payment_webhook_provider_event UNIQUE (provider, event_id),
    CONSTRAINT ck_payment_webhook_status CHECK (status IN ('RECEIVED','PROCESSED','FAILED','IGNORED'))
);

CREATE INDEX IF NOT EXISTS idx_payment_webhook_payment_id ON payment_webhook_events(payment_id);
