package com.school.app.billing;

import com.school.app.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * MT-5 (manual/offline billing) — a school's self-reported record of a Demand Draft, Cheque, or
 * NEFT payment made outside the app. Starts {@code PENDING_VERIFICATION}; an operator checks the
 * school's bank statement and verifies or rejects it (see {@code PlatformPaymentService}) — there
 * is no payment gateway, so nothing here is auto-confirmed. Verifying extends the subscription's
 * billing period and reactivates the school; nothing here ever auto-suspends one (see
 * {@code SubscriptionOverdueJob}'s Javadoc for why).
 */
@Entity
@Table(name = "payment_claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentClaim {

    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    /** DD number, cheque number, or NEFT UTR — whatever the school's bank slip shows. */
    @Column(name = "reference_number", nullable = false)
    private String referenceNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentClaimStatus status = PaymentClaimStatus.PENDING_VERIFICATION;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    /**
     * The verifying/rejecting {@code PlatformUser}'s id — a plain column, not a JPA association,
     * since {@code PlatformUser} is a global entity outside this (tenant-owned) entity's
     * {@code @TenantId} boundary and platform actions here go through native bypass queries anyway
     * (see {@code PaymentClaimRepository}).
     */
    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Operator notes — typically a rejection reason. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    void onCreate() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
        if (status == null) {
            status = PaymentClaimStatus.PENDING_VERIFICATION;
        }
    }
}
