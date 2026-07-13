package com.school.app.platform;

import com.school.app.school.School;
import com.school.app.school.SchoolStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Global (not {@code @TenantId}) — one row per school. {@code status} mirrors/drives the owning
 * {@link School#getStatus()}; MT-1's lifecycle enforcement in {@code JwtAuthFilter} reads the
 * school row directly rather than this one, since that's what every request already resolves.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, unique = true)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SchoolStatus status;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
