package com.school.app.common.notification;

import com.school.app.user.User;

public interface NotificationService {

    /**
     * Notifies {@code recipient} about {@code eventType}, sending on whichever channels are
     * currently enabled for that event type (see {@link NotificationPreference}). Never throws —
     * a notification failure must not break the business operation that triggered it. Every
     * attempted channel writes a {@link NotificationLog} row regardless of outcome.
     */
    void notify(NotificationEventType eventType, User recipient, String subject, String message);
}
