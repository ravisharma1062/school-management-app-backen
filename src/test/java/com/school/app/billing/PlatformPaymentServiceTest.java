package com.school.app.billing;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.security.TenantContext;
import com.school.app.common.security.TenantRlsTransactionListener;
import com.school.app.platform.AuditAction;
import com.school.app.platform.AuditService;
import com.school.app.platform.PlatformRole;
import com.school.app.platform.PlatformUser;
import com.school.app.platform.Subscription;
import com.school.app.platform.SubscriptionRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.user.Role;
import com.school.app.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformPaymentServiceTest {

    @Mock
    private PaymentClaimRepository paymentClaimRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private TenantRlsTransactionListener tenantRlsTransactionListener;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PlatformPaymentService platformPaymentService;

    private UUID schoolId;
    private School school;
    private PlatformUser actor;
    private PaymentClaim pendingClaim;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        school = School.builder().id(schoolId).name("Springfield High").slug("springfield-high")
                .status(SchoolStatus.PAST_DUE).build();
        actor = PlatformUser.builder().id(UUID.randomUUID()).email("operator@school.app")
                .platformRole(PlatformRole.PLATFORM_ADMIN).build();
        User submitter = User.builder().id(UUID.randomUUID()).schoolId(schoolId).name("Admin")
                .email("admin@school.app").passwordHash("hashed").role(Role.ADMIN).build();
        pendingClaim = PaymentClaim.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .amount(new BigDecimal("4999.00"))
                .method(PaymentMethod.NEFT)
                .referenceNumber("UTR12345")
                .periodStart(LocalDate.of(2026, 7, 1))
                .periodEnd(LocalDate.of(2026, 7, 31))
                .status(PaymentClaimStatus.PENDING_VERIFICATION)
                .submittedBy(submitter)
                .submittedAt(Instant.now())
                .build();
        subscription = Subscription.builder().id(UUID.randomUUID()).school(school)
                .status(SchoolStatus.PAST_DUE).build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void verifyExtendsTheBillingPeriodAndReactivatesTheSchool() {
        when(paymentClaimRepository.findByIdBypassingTenantFilter(pendingClaim.getId()))
                .thenReturn(Optional.of(pendingClaim));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(subscription));

        PlatformPaymentDto dto = platformPaymentService.verify(
                pendingClaim.getId(), new PaymentDecisionRequest("looks good"), actor);

        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        verify(schoolRepository).save(school);
        assertThat(subscription.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodStart())
                .isEqualTo(LocalDate.of(2026, 7, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(subscription.getCurrentPeriodEnd())
                .isEqualTo(LocalDate.of(2026, 7, 31).atStartOfDay(ZoneOffset.UTC).toInstant());
        verify(subscriptionRepository).save(subscription);
        verify(paymentClaimRepository).updateVerificationBypassingTenantFilter(
                eq(pendingClaim.getId()), eq(PaymentClaimStatus.VERIFIED.name()), eq(actor.getId()), any(Instant.class), eq("looks good"));
        verify(auditService).record(eq(actor), eq(AuditAction.PAYMENT_VERIFIED), eq(schoolId), contains("Springfield High"));
        // Bypass writes to a @TenantId table need the RLS session variable set for the target school first.
        assertThat(TenantContext.get()).isEqualTo(schoolId);
        verify(tenantRlsTransactionListener).applyCurrentTenant(entityManager);
        assertThat(dto.id()).isEqualTo(pendingClaim.getId());
    }

    @Test
    void verifyRejectsAClaimThatIsNotPending() {
        pendingClaim.setStatus(PaymentClaimStatus.VERIFIED);
        when(paymentClaimRepository.findByIdBypassingTenantFilter(pendingClaim.getId()))
                .thenReturn(Optional.of(pendingClaim));

        assertThatThrownBy(() -> platformPaymentService.verify(
                pendingClaim.getId(), new PaymentDecisionRequest(null), actor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("verified");
        verify(schoolRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void verifyThrowsWhenClaimDoesNotExist() {
        when(paymentClaimRepository.findByIdBypassingTenantFilter(pendingClaim.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformPaymentService.verify(
                pendingClaim.getId(), new PaymentDecisionRequest(null), actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyThrowsWhenSchoolHasNoSubscription() {
        when(paymentClaimRepository.findByIdBypassingTenantFilter(pendingClaim.getId()))
                .thenReturn(Optional.of(pendingClaim));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformPaymentService.verify(
                pendingClaim.getId(), new PaymentDecisionRequest(null), actor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("subscription");
    }

    @Test
    void rejectMarksTheClaimRejectedWithoutTouchingTheSchoolOrSubscription() {
        when(paymentClaimRepository.findByIdBypassingTenantFilter(pendingClaim.getId()))
                .thenReturn(Optional.of(pendingClaim))
                .thenReturn(Optional.of(pendingClaim));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        PlatformPaymentDto dto = platformPaymentService.reject(
                pendingClaim.getId(), new PaymentDecisionRequest("statement doesn't show this NEFT"), actor);

        verify(paymentClaimRepository).updateVerificationBypassingTenantFilter(
                eq(pendingClaim.getId()), eq(PaymentClaimStatus.REJECTED.name()), eq(actor.getId()), any(Instant.class),
                eq("statement doesn't show this NEFT"));
        verify(schoolRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());
        verify(auditService).record(eq(actor), eq(AuditAction.PAYMENT_REJECTED), eq(schoolId),
                contains("statement doesn't show this NEFT"));
        assertThat(dto.id()).isEqualTo(pendingClaim.getId());
    }

    @Test
    void rejectRejectsAClaimThatIsNotPending() {
        pendingClaim.setStatus(PaymentClaimStatus.REJECTED);
        when(paymentClaimRepository.findByIdBypassingTenantFilter(pendingClaim.getId()))
                .thenReturn(Optional.of(pendingClaim));

        assertThatThrownBy(() -> platformPaymentService.reject(
                pendingClaim.getId(), new PaymentDecisionRequest(null), actor))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listPendingReturnsOnlyPendingVerificationClaimsMappedToDtos() {
        when(paymentClaimRepository.findAllByStatusBypassingTenantFilter(PaymentClaimStatus.PENDING_VERIFICATION.name()))
                .thenReturn(java.util.List.of(pendingClaim));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        var result = platformPaymentService.listPending();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).schoolName()).isEqualTo("Springfield High");
        assertThat(result.get(0).status()).isEqualTo(PaymentClaimStatus.PENDING_VERIFICATION);
    }

    @Test
    void toDtoFallsBackToUnknownWhenTheSchoolWasDeleted() {
        when(paymentClaimRepository.findAllByStatusBypassingTenantFilter(PaymentClaimStatus.PENDING_VERIFICATION.name()))
                .thenReturn(java.util.List.of(pendingClaim));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.empty());

        var result = platformPaymentService.listPending();

        assertThat(result.get(0).schoolName()).isEqualTo("Unknown");
    }
}
