package com.school.app.platform;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Global (not {@code @TenantId}) — a captured lead from the (not-yet-built, MT-4) public signup
 * form. Table created now per the plan's MT-2 build order; nothing populates it until MT-4.
 */
@Entity
@Table(name = "signup_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_name", nullable = false)
    private String schoolName;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_plan", nullable = false, length = 20)
    private PlanCode desiredPlan;

    @Column(name = "wants_email", nullable = false)
    private boolean wantsEmail;

    @Column(name = "wants_sms", nullable = false)
    private boolean wantsSms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SignupRequestStatus status = SignupRequestStatus.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
