package com.school.app.platform;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Global (not {@code @TenantId}) — a catalog of plans shared across every school. */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private PlanCode code;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    private BillingInterval billingInterval;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
