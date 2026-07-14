-- Phase MT-6b: self-service free trial.

-- Self-service trial provisioning has no human platform-user actor — relax the FK so
-- AuditService can record it with a null actor (AuditLogDto/PlatformAuditLogService render a
-- "System (self-service)" fallback label in place of an actor email).
ALTER TABLE audit_logs ALTER COLUMN platform_user_id DROP NOT NULL;

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN ('SIGNUP_REQUEST_APPROVED', 'SIGNUP_REQUEST_REJECTED',
                       'SCHOOL_STATUS_CHANGED', 'SUBSCRIPTION_PLAN_CHANGED',
                       'TRIAL_SELF_PROVISIONED'));
