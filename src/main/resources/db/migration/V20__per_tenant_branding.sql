-- Phase MT-6a: per-tenant branding. Logo + brand colors live directly on schools (the tenant
-- root, already global/non-@TenantId) rather than a separate table — there's exactly one branding
-- record per school, so a join buys nothing.

ALTER TABLE schools ADD COLUMN logo_key VARCHAR(500);
ALTER TABLE schools ADD COLUMN logo_content_type VARCHAR(100);
ALTER TABLE schools ADD COLUMN primary_color VARCHAR(7);
ALTER TABLE schools ADD COLUMN secondary_color VARCHAR(7);

ALTER TABLE entitlements DROP CONSTRAINT entitlements_feature_key_check;
ALTER TABLE entitlements ADD CONSTRAINT entitlements_feature_key_check
    CHECK (feature_key IN ('EMAIL_NOTIFICATIONS', 'SMS_NOTIFICATIONS', 'ONLINE_PAYMENTS',
                            'MESSAGING', 'TRANSPORT_TRACKING', 'LIBRARY', 'ANALYTICS',
                            'MAX_STUDENTS', 'BRANDING'));

-- Backfill a BRANDING entitlement for every existing subscription, matching PlanDefaults.java
-- (on for PREMIUM, off for BASIC/STANDARD) — the same rule ProvisioningService/
-- PlatformSubscriptionService apply when creating/changing a subscription going forward.
INSERT INTO entitlements (subscription_id, feature_key, enabled, limit_value)
SELECT s.id, 'BRANDING', (sp.code = 'PREMIUM'), NULL
FROM subscriptions s
JOIN subscription_plans sp ON sp.id = s.plan_id
WHERE NOT EXISTS (
    SELECT 1 FROM entitlements e WHERE e.subscription_id = s.id AND e.feature_key = 'BRANDING'
);
