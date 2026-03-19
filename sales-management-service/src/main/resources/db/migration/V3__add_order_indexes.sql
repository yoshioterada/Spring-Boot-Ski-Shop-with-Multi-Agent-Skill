-- Composite index for customer order queries (N+1 and pagination)
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);
