package com.school.app.common.notification;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    private NotificationEventType eventType;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    boolean isEnabled(NotificationChannel channel) {
        return switch (channel) {
            case SMS -> smsEnabled;
            case EMAIL -> emailEnabled;
        };
    }
}
