package com.school.app.billing;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.security.TenantContext;
import com.school.app.platform.PlatformSettings;
import com.school.app.platform.PlatformSettingsRepository;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final PaymentClaimRepository paymentClaimRepository;
    private final PlatformSettingsRepository platformSettingsRepository;

    @Transactional(readOnly = true)
    public String getPaymentInstructions() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .map(PlatformSettings::getPaymentInstructions)
                .orElse(null);
    }

    @Transactional
    public PaymentClaimDto submit(PaymentClaimCreateRequest request, User currentUser) {
        if (!request.periodEnd().isAfter(request.periodStart())) {
            throw new BadRequestException("periodEnd must be after periodStart");
        }

        PaymentClaim saved = paymentClaimRepository.save(PaymentClaim.builder()
                .schoolId(TenantContext.get())
                .amount(request.amount())
                .method(request.method())
                .referenceNumber(request.referenceNumber().trim())
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .submittedBy(currentUser)
                .build());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PaymentClaimDto> getMyHistory(Pageable pageable) {
        return paymentClaimRepository.findAllByOrderBySubmittedAtDesc(pageable).map(this::toDto);
    }

    private PaymentClaimDto toDto(PaymentClaim claim) {
        return new PaymentClaimDto(
                claim.getId(), claim.getAmount(), claim.getMethod(), claim.getReferenceNumber(),
                claim.getPeriodStart(), claim.getPeriodEnd(), claim.getStatus(),
                claim.getSubmittedAt(), claim.getVerifiedAt(), claim.getNotes());
    }
}
