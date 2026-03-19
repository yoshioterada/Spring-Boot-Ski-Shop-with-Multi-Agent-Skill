-- Add missing columns and indexes

ALTER TABLE orders ADD COLUMN payment_method VARCHAR(50);
ALTER TABLE orders ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN used_points INTEGER DEFAULT 0;
ALTER TABLE orders ADD COLUMN point_discount_amount NUMERIC(12,2) DEFAULT 0.00;

ALTER TABLE returns ADD COLUMN quantity INTEGER;
ALTER TABLE returns ADD COLUMN refund_amount NUMERIC(12,2);

CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_shipments_status ON shipments(status);
CREATE INDEX idx_returns_status ON returns(status);
