-- Phase MT-3: operator console & provisioning.

-- The first admin of a newly-provisioned school starts PENDING_ACTIVATION (no usable password —
-- see ProvisioningService) and only becomes ACTIVE once they set their own password via the
-- invite link. Existing rows all default to ACTIVE (they can already log in today).
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE'));

-- Global (no school_id/@TenantId as a JPA relation) — deliberately stores school_id/user_id as
-- plain columns rather than JPA associations to a @TenantId entity, since the activation endpoints
-- run before any tenant is known (same bootstrapping problem as login). Only a hash of the token
-- is stored; the raw token exists only in the emailed link.
CREATE TABLE activation_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    UUID NOT NULL REFERENCES schools (id),
    user_id      UUID NOT NULL,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMP NOT NULL,
    used         BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_activation_tokens_user_id ON activation_tokens (user_id);

-- Every cross-tenant (platform) action writes one of these. target_school_id is nullable — a
-- rejected signup request never became a school.
CREATE TABLE audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_user_id  UUID NOT NULL REFERENCES platform_users (id),
    action            VARCHAR(40) NOT NULL
                          CHECK (action IN ('SIGNUP_REQUEST_APPROVED', 'SIGNUP_REQUEST_REJECTED',
                                             'SCHOOL_STATUS_CHANGED', 'SUBSCRIPTION_PLAN_CHANGED')),
    target_school_id  UUID,
    summary           TEXT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_target_school_id ON audit_logs (target_school_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);

-- Dev-only bootstrap platform admin so the operator console has a way to log in. Not MFA-enrolled
-- by default (mfa_secret NULL) — MFA is required only once a platform admin has actually enrolled,
-- to avoid locking out the only seeded account before invite/reset emails are configured.
-- Password: Operator@123. Change or remove this seed before any non-local deployment.
INSERT INTO platform_users (name, email, password_hash, platform_role)
VALUES ('Platform Admin', 'operator@school.app', crypt('Operator@123', gen_salt('bf', 10)), 'PLATFORM_ADMIN');
