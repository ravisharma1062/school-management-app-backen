package com.school.app.platform;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Global (not {@code @TenantId}) — normalized so it's easy to query/report on directly. */
@Entity
@Table(name = "entitlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entitlement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 30)
    private FeatureKey featureKey;

    @Column(nullable = false)
    private boolean enabled;

    /** Quota ceiling for limit-style keys (e.g. {@link FeatureKey#MAX_STUDENTS}); null = unlimited. */
    @Column(name = "limit_value")
    private Integer limitValue;
}
