-- Phase 9: Outbox relay retry tracking and failed-event observability.
-- Rollback note: columns are retained to preserve retry/error audit history.

ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_event_outbox_relay
ON event_outbox (status, next_retry_at, created_at);