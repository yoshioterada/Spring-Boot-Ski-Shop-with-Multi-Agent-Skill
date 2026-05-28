ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS ck_orders_payment_status;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_payment_status CHECK (
        payment_status IN ('PENDING','AUTHORIZED','CAPTURED','REFUNDED','FAILED','CANCELLED')
    );