package com.school.app.billing;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.security.TenantContext;
import com.school.app.platform.PlatformSettings;
import com.school.app.platform.PlatformSettingsRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private PaymentClaimRepository paymentClaimRepository;
    @Mock
    private PlatformSettingsRepository platformSettingsRepository;

    @InjectMocks
    private BillingService billingService;

    private UUID schoolId;
    private User billingOwner;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        TenantContext.set(schoolId);
        billingOwner = User.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .name("Admin")
                .email("admin@school.app")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void submitSavesAPendingClaimScopedToTheCurrentTenant() {
        when(paymentClaimRepository.save(any(PaymentClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        PaymentClaimCreateRequest request = new PaymentClaimCreateRequest(
                new BigDecimal("4999.00"), PaymentMethod.NEFT, "  UTR12345  ",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        PaymentClaimDto dto = billingService.submit(request, billingOwner);

        ArgumentCaptor<PaymentClaim> captor = ArgumentCaptor.forClass(PaymentClaim.class);
        verify(paymentClaimRepository).save(captor.capture());
        PaymentClaim saved = captor.getValue();
        assertThat(saved.getSchoolId()).isEqualTo(schoolId);
        assertThat(saved.getReferenceNumber()).isEqualTo("UTR12345");
        assertThat(saved.getSubmittedBy()).isEqualTo(billingOwner);
        assertThat(saved.getStatus()).isEqualTo(PaymentClaimStatus.PENDING_VERIFICATION);
        assertThat(dto.amount()).isEqualByComparingTo("4999.00");
        assertThat(dto.method()).isEqualTo(PaymentMethod.NEFT);
        assertThat(dto.status()).isEqualTo(PaymentClaimStatus.PENDING_VERIFICATION);
    }

    @Test
    void submitRejectsAPeriodEndNotAfterThePeriodStart() {
        PaymentClaimCreateRequest samePeriod = new PaymentClaimCreateRequest(
                new BigDecimal("4999.00"), PaymentMethod.CHEQUE, "CHQ001",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));

        assertThatThrownBy(() -> billingService.submit(samePeriod, billingOwner))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("periodEnd");
        verify(paymentClaimRepository, never()).save(any());
    }

    @Test
    void submitRejectsAnInvertedPeriod() {
        PaymentClaimCreateRequest inverted = new PaymentClaimCreateRequest(
                new BigDecimal("4999.00"), PaymentMethod.DEMAND_DRAFT, "DD9",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1));

        assertThatThrownBy(() -> billingService.submit(inverted, billingOwner))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void paymentInstructionsComeFromTheSingletonSettingsRow() {
        when(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID))
                .thenReturn(Optional.of(PlatformSettings.builder()
                        .id(PlatformSettings.SINGLETON_ID)
                        .paymentInstructions("NEFT to A/C 1234, IFSC ABCD0001234")
                        .build()));

        assertThat(billingService.getPaymentInstructions()).isEqualTo("NEFT to A/C 1234, IFSC ABCD0001234");
    }

    @Test
    void paymentInstructionsAreNullWhenTheSettingsRowIsMissing() {
        when(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThat(billingService.getPaymentInstructions()).isNull();
    }

    @Test
    void myHistoryMapsClaimsToDtos() {
        PaymentClaim claim = PaymentClaim.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .amount(new BigDecimal("4999.00"))
                .method(PaymentMethod.NEFT)
                .referenceNumber("UTR12345")
                .periodStart(LocalDate.of(2026, 7, 1))
                .periodEnd(LocalDate.of(2026, 7, 31))
                .status(PaymentClaimStatus.VERIFIED)
                .submittedBy(billingOwner)
                .submittedAt(Instant.parse("2026-07-02T10:00:00Z"))
                .verifiedAt(Instant.parse("2026-07-03T09:00:00Z"))
                .notes("verified against statement")
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentClaimRepository.findAllByOrderBySubmittedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));

        Page<PaymentClaimDto> page = billingService.getMyHistory(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        PaymentClaimDto dto = page.getContent().get(0);
        assertThat(dto.id()).isEqualTo(claim.getId());
        assertThat(dto.referenceNumber()).isEqualTo("UTR12345");
        assertThat(dto.status()).isEqualTo(PaymentClaimStatus.VERIFIED);
        assertThat(dto.verifiedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
        assertThat(dto.notes()).isEqualTo("verified against statement");
    }
}
