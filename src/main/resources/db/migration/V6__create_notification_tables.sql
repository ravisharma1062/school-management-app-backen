CREATE TABLE notification_preferences (
    event_type    VARCHAR(50) PRIMARY KEY,
    sms_enabled   BOOLEAN NOT NULL DEFAULT false,
    email_enabled BOOLEAN NOT NULL DEFAULT false
);

-- Sensible defaults; admins can change these later via PATCH /api/v1/notification-preferences/{eventType}.
-- Push notifications (notices, homework) are handled separately by the existing FCM-topic
-- broadcast in PushNotificationService, which has no per-recipient concept and so isn't
-- gated by this table.
INSERT INTO notification_preferences (event_type, sms_enabled, email_enabled) VALUES
    ('ATTENDANCE_ABSENT', true, false),
    ('FEE_OVERDUE', true, false),
    ('NOTICE_CREATED', false, true),
    ('EXAM_RESULT_PUBLISHED', false, true),
    ('USER_WELCOME', false, true),
    ('MESSAGE_RECEIVED', false, false);

CREATE TABLE notification_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR(50) NOT NULL,
    channel     VARCHAR(10) NOT NULL CHECK (channel IN ('SMS', 'EMAIL')),
    recipient   VARCHAR(255) NOT NULL,
    subject     VARCHAR(255),
    status      VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    error       TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_log_event_type ON notification_log (event_type);
CREATE INDEX idx_notification_log_created_at ON notification_log (created_at);
