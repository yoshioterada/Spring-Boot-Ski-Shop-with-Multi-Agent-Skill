-- Add optimistic locking version column
ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
