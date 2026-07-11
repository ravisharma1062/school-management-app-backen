CREATE TABLE payments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_id             UUID NOT NULL REFERENCES fees (id),
    amount             DECIMAL(10, 2) NOT NULL,
    gateway_order_id   VARCHAR(255) NOT NULL UNIQUE,
    gateway_payment_id VARCHAR(255),
    status             VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    initiated_by       UUID NOT NULL REFERENCES users (id),
    paid_at            TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_fee_id ON payments (fee_id);
