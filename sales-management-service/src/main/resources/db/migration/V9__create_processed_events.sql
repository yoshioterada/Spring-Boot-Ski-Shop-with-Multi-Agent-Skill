CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_error TEXT,
    CONSTRAINT ck_processed_events_status CHECK (status IN ('PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_processed_events_consumer_type
    ON processed_events (consumer_name, event_type, processed_at);