-- Add missing columns to payments table
ALTER TABLE payments ADD COLUMN failure_reason VARCHAR(500);
ALTER TABLE payments ADD COLUMN refunded_amount NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE payments ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payments ADD COLUMN gateway_provider VARCHAR(50);

-- Add missing indexes
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_carts_expires_at ON carts(expires_at);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);
