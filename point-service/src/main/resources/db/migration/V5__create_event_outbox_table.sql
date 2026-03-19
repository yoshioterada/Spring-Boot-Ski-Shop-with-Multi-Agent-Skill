-- Phase 3: Transactional Outbox pattern table for reliable event publishing
-- This table stores events within the same database transaction as business operations,
-- ensuring at-least-once delivery when combined with a relay process (Debezium CDC or polling).

CREATE TABLE event_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(50) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    producer        VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    correlation_id  VARCHAR(50),
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Partial index for efficient polling of pending events
CREATE INDEX idx_outbox_status_pending ON event_outbox(created_at) WHERE status = 'PENDING';
