-- V6: Add index on returns.customer_id for efficient customer-based queries
CREATE INDEX idx_returns_customer_id ON returns(customer_id);
