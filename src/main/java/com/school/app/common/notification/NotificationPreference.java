package com.school.app.common.notification;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    // Was the @Id on its own (one global row per event type) before multi-tenancy; event_type
    // alone can no longer be unique once every school has its own row, so it's now a plain
    // column and the table gets a generated id + a UNIQUE(school_id, event_type) constraint.
    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
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
