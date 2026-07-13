-- Phase MT-2: the platform tier above schools. These 5 tables are global (no school_id, no
-- @TenantId) — a school's own subscription/entitlements are looked up by school_id, not filtered
-- by it, since the concept of "this school's plan" has to be readable even for lifecycle checks
-- that run before/around the tenant filter.

CREATE TABLE subscription_plans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(20) NOT NULL UNIQUE CHECK (code IN ('BASIC', 'STANDARD', 'PREMIUM')),
    name             VARCHAR(100) NOT NULL,
    base_price       DECIMAL(10, 2) NOT NULL,
    billing_interval VARCHAR(10) NOT NULL CHECK (billing_interval IN ('MONTHLY', 'ANNUAL')),
    active           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE subscriptions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL UNIQUE REFERENCES schools (id),
    plan_id               UUID NOT NULL REFERENCES subscription_plans (id),
    status                VARCHAR(20) NOT NULL
                              CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    current_period_start  TIMESTAMP,
    current_period_end    TIMESTAMP,
    trial_ends_at         TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE entitlements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions (id),
    feature_key     VARCHAR(30) NOT NULL
                        CHECK (feature_key IN ('EMAIL_NOTIFICATIONS', 'SMS_NOTIFICATIONS', 'ONLINE_PAYMENTS',
                                                'MESSAGING', 'TRANSPORT_TRACKING', 'LIBRARY', 'ANALYTICS',
                                                'MAX_STUDENTS')),
    enabled         BOOLEAN NOT NULL,
    limit_value     INT,
    CONSTRAINT uq_entitlements_subscription_feature UNIQUE (subscription_id, feature_key)
);

CREATE TABLE platform_users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    platform_role VARCHAR(20) NOT NULL CHECK (platform_role IN ('PLATFORM_ADMIN')),
    mfa_secret    VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- Populated starting MT-4 (public signup form); the table is created now per the plan's MT-2
-- build order so MT-3's operator queue has somewhere to read from as soon as it exists.
CREATE TABLE signup_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_name   VARCHAR(255) NOT NULL,
    contact_name  VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(20),
    desired_plan  VARCHAR(20) NOT NULL CHECK (desired_plan IN ('BASIC', 'STANDARD', 'PREMIUM')),
    wants_email   BOOLEAN NOT NULL DEFAULT false,
    wants_sms     BOOLEAN NOT NULL DEFAULT false,
    status        VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'APPROVED', 'REJECTED')),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_school_id ON subscriptions (school_id);
CREATE INDEX idx_entitlements_subscription_id ON entitlements (subscription_id);

-- NotificationServiceImpl now checks EMAIL_NOTIFICATIONS/SMS_NOTIFICATIONS entitlement before
-- dispatch and logs this status when a channel is enabled in notification_preferences but not
-- in the school's plan — sits alongside the existing "enabled but not configured" SKIPPED case.
ALTER TABLE notification_log DROP CONSTRAINT notification_log_status_check;
ALTER TABLE notification_log ADD CONSTRAINT notification_log_status_check
    CHECK (status IN ('SENT', 'FAILED', 'SKIPPED', 'SKIPPED_NOT_ENTITLED'));

DO $$
DECLARE
    v_premium_id UUID;
    v_school_id UUID;
    v_subscription_id UUID;
BEGIN
    INSERT INTO subscription_plans (code, name, base_price, billing_interval, active)
    VALUES ('BASIC', 'Basic', 999.00, 'MONTHLY', true);

    INSERT INTO subscription_plans (code, name, base_price, billing_interval, active)
    VALUES ('STANDARD', 'Standard', 2499.00, 'MONTHLY', true);

    INSERT INTO subscription_plans (code, name, base_price, billing_interval, active)
    VALUES ('PREMIUM', 'Premium', 4999.00, 'MONTHLY', true)
    RETURNING id INTO v_premium_id;

    -- Backfill: the one pre-existing school used every module without restriction before
    -- entitlements existed (payments, messaging, transport, library, analytics were all already
    -- built and live) — give it Premium with everything enabled and no student cap, matching
    -- "whatever it uses today" rather than an arbitrary default that could suddenly disable
    -- something that was working.
    SELECT id INTO v_school_id FROM schools WHERE slug = 'default-school';

    INSERT INTO subscriptions (school_id, plan_id, status, current_period_start, current_period_end)
    VALUES (v_school_id, v_premium_id, 'ACTIVE', now(), now() + interval '100 years')
    RETURNING id INTO v_subscription_id;

    INSERT INTO entitlements (subscription_id, feature_key, enabled, limit_value) VALUES
        (v_subscription_id, 'EMAIL_NOTIFICATIONS', true, NULL),
        (v_subscription_id, 'SMS_NOTIFICATIONS', true, NULL),
        (v_subscription_id, 'ONLINE_PAYMENTS', true, NULL),
        (v_subscription_id, 'MESSAGING', true, NULL),
        (v_subscription_id, 'TRANSPORT_TRACKING', true, NULL),
        (v_subscription_id, 'LIBRARY', true, NULL),
        (v_subscription_id, 'ANALYTICS', true, NULL),
        (v_subscription_id, 'MAX_STUDENTS', true, NULL);
END $$;
