-- Add optimistic locking version column
ALTER TABLE point_transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
