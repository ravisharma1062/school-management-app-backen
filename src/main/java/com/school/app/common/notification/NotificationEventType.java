package com.school.app.common.notification;

/** Every event type that can trigger a notification, each with its own per-channel preference row. */
public enum NotificationEventType {
    ATTENDANCE_ABSENT,
    FEE_OVERDUE,
    NOTICE_CREATED,
    HOMEWORK_CREATED,
    EXAM_RESULT_PUBLISHED,
    USER_WELCOME,
    MESSAGE_RECEIVED
}
