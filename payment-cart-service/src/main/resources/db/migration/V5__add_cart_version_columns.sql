-- V5: Add version columns for optimistic locking on carts and cart_items
ALTER TABLE carts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cart_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
