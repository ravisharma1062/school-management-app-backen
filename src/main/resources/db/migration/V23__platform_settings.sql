-- Phase MT-6f: self-service automated provisioning (evolution of MT-3's operator-approved flow).

-- Global (no school_id) singleton — one row, seeded below, holding platform-wide toggles.
CREATE TABLE platform_settings (
    id                    UUID PRIMARY KEY,
    auto_approve_signups  BOOLEAN NOT NULL DEFAULT false,
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

-- Defaults to false — "keep vetting for paid conversion" per the plan, until an operator
-- explicitly opts in from the operator console once volume justifies it.
INSERT INTO platform_settings (id, auto_approve_signups)
VALUES ('00000000-0000-0000-0000-000000000001', false);

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN ('SIGNUP_REQUEST_APPROVED', 'SIGNUP_REQUEST_REJECTED',
                       'SCHOOL_STATUS_CHANGED', 'SUBSCRIPTION_PLAN_CHANGED',
                       'TRIAL_SELF_PROVISIONED', 'PLATFORM_SETTINGS_UPDATED'));
