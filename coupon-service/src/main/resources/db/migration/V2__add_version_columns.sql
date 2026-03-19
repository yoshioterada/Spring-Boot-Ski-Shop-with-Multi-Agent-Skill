-- Add optimistic locking version columns
ALTER TABLE coupons ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_coupons ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
