-- Phase MT-5 (manual/offline billing) — Demand Draft, Cheque, and NEFT payments, verified by an
-- operator instead of a payment gateway (no gateway KYC/e-mandate/PCI dependency for launch;
-- online payment gateway integration is a documented future phase, not this one).

CREATE TABLE payment_claims (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools (id),
    amount            NUMERIC(10, 2) NOT NULL,
    method            VARCHAR(20) NOT NULL CHECK (method IN ('DEMAND_DRAFT', 'CHEQUE', 'NEFT')),
    reference_number  VARCHAR(100) NOT NULL,
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                          CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED')),
    submitted_by      UUID NOT NULL REFERENCES users (id),
    submitted_at      TIMESTAMP NOT NULL DEFAULT now(),
    verified_by       UUID,
    verified_at       TIMESTAMP,
    notes             TEXT
);
CREATE INDEX idx_payment_claims_school_id ON payment_claims (school_id);
CREATE INDEX idx_payment_claims_status ON payment_claims (status);

-- Same defense-in-depth as every other tenant-owned table (see V17's header comment).
ALTER TABLE payment_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_claims FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payment_claims USING (school_id = current_setting('app.current_school_id', true)::uuid);

-- Shown to every school on its billing page: bank details for NEFT, cheque/DD payee, etc.
ALTER TABLE platform_settings ADD COLUMN payment_instructions TEXT;

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN ('SIGNUP_REQUEST_APPROVED', 'SIGNUP_REQUEST_REJECTED',
                       'SCHOOL_STATUS_CHANGED', 'SUBSCRIPTION_PLAN_CHANGED',
                       'TRIAL_SELF_PROVISIONED', 'PLATFORM_SETTINGS_UPDATED',
                       'PAYMENT_VERIFIED', 'PAYMENT_REJECTED', 'SUBSCRIPTION_MARKED_PAST_DUE'));
