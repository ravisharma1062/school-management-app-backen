package com.school.app.billing;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.security.TenantContext;
import com.school.app.common.security.TenantRlsTransactionListener;
import com.school.app.platform.AuditAction;
import com.school.app.platform.AuditService;
import com.school.app.platform.PlatformUser;
import com.school.app.platform.Subscription;
import com.school.app.platform.SubscriptionRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MT-5 (manual/offline billing) — the operator side of the DD/Cheque/NEFT flow: verifying a claim
 * extends the subscription's billing period and reactivates the school; nothing here ever
 * auto-suspends one, matching the plan's own "no card/mandate credentials touch our servers"
 * spirit — this app doesn't process payments at all, an operator just confirms one arrived.
 */
@Service
@RequiredArgsConstructor
public class PlatformPaymentService {

    private final PaymentClaimRepository paymentClaimRepository;
    private final SchoolRepository schoolRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AuditService auditService;
    private final TenantRlsTransactionListener tenantRlsTransactionListener;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<PlatformPaymentDto> listPending() {
        return paymentClaimRepository.findAllByStatusBypassingTenantFilter(PaymentClaimStatus.PENDING_VERIFICATION.name())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public PlatformPaymentDto verify(UUID claimId, PaymentDecisionRequest request, PlatformUser actor) {
        PaymentClaim claim = requirePendingClaim(claimId);
        School school = requireSchool(claim.getSchoolId());
        Subscription subscription = subscriptionRepository.findBySchoolId(claim.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found for school " + claim.getSchoolId()));

        applyBypassWriteContext(claim.getSchoolId());
        paymentClaimRepository.updateVerificationBypassingTenantFilter(
                claimId, PaymentClaimStatus.VERIFIED.name(), actor.getId(), Instant.now(), request.notes());

        school.setStatus(SchoolStatus.ACTIVE);
        schoolRepository.save(school);
        subscription.setStatus(SchoolStatus.ACTIVE);
        subscription.setCurrentPeriodStart(claim.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant());
        subscription.setCurrentPeriodEnd(claim.getPeriodEnd().atStartOfDay(ZoneOffset.UTC).toInstant());
        subscriptionRepository.save(subscription);

        auditService.record(actor, AuditAction.PAYMENT_VERIFIED, claim.getSchoolId(),
                "Verified " + claim.getMethod() + " payment of " + claim.getAmount() + " for '" + school.getName()
                        + "' (ref " + claim.getReferenceNumber() + "), period now " + claim.getPeriodStart() + " to " + claim.getPeriodEnd());

        return toDto(requireClaim(claimId));
    }

    @Transactional
    public PlatformPaymentDto reject(UUID claimId, PaymentDecisionRequest request, PlatformUser actor) {
        PaymentClaim claim = requirePendingClaim(claimId);
        School school = requireSchool(claim.getSchoolId());

        applyBypassWriteContext(claim.getSchoolId());
        paymentClaimRepository.updateVerificationBypassingTenantFilter(
                claimId, PaymentClaimStatus.REJECTED.name(), actor.getId(), Instant.now(), request.notes());

        auditService.record(actor, AuditAction.PAYMENT_REJECTED, claim.getSchoolId(),
                "Rejected " + claim.getMethod() + " payment claim of " + claim.getAmount() + " for '" + school.getName()
                        + "' (ref " + claim.getReferenceNumber() + ")"
                        + (request.notes() != null && !request.notes().isBlank() ? ": " + request.notes() : ""));

        return toDto(requireClaim(claimId));
    }

    /** Bypass writes to this @TenantId table need the RLS session variable set for the target school first — see ActivationService's identical need. */
    private void applyBypassWriteContext(UUID schoolId) {
        TenantContext.set(schoolId);
        tenantRlsTransactionListener.applyCurrentTenant(entityManager);
    }

    private PaymentClaim requirePendingClaim(UUID id) {
        PaymentClaim claim = requireClaim(id);
        if (claim.getStatus() != PaymentClaimStatus.PENDING_VERIFICATION) {
            throw new BadRequestException("This payment claim has already been " + claim.getStatus().name().toLowerCase(Locale.ROOT));
        }
        return claim;
    }

    private PaymentClaim requireClaim(UUID id) {
        return paymentClaimRepository.findByIdBypassingTenantFilter(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment claim " + id + " not found"));
    }

    private School requireSchool(UUID id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School " + id + " not found"));
    }

    private PlatformPaymentDto toDto(PaymentClaim claim) {
        String schoolName = schoolRepository.findById(claim.getSchoolId()).map(School::getName).orElse("Unknown");
        return new PlatformPaymentDto(
                claim.getId(), claim.getSchoolId(), schoolName, claim.getAmount(), claim.getMethod(),
                claim.getReferenceNumber(), claim.getPeriodStart(), claim.getPeriodEnd(), claim.getStatus(),
                claim.getSubmittedBy().getEmail(), claim.getSubmittedAt(), claim.getVerifiedAt(), claim.getNotes());
    }
}
