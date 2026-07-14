package com.school.app.platform;

import com.school.app.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository platformSettingsRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PlatformSettingsDto get() {
        return toDto(requireSettings());
    }

    @Transactional
    public PlatformSettingsDto update(PlatformSettingsUpdateRequest request, PlatformUser actor) {
        PlatformSettings settings = requireSettings();
        StringBuilder changeSummary = new StringBuilder();

        if (request.autoApproveSignups() != null && request.autoApproveSignups() != settings.isAutoApproveSignups()) {
            changeSummary.append("auto-approve-signups: ").append(settings.isAutoApproveSignups())
                    .append(" -> ").append(request.autoApproveSignups()).append(". ");
            settings.setAutoApproveSignups(request.autoApproveSignups());
        }
        if (request.paymentInstructions() != null) {
            changeSummary.append("Payment instructions updated.");
            settings.setPaymentInstructions(request.paymentInstructions());
        }

        PlatformSettings saved = platformSettingsRepository.save(settings);
        if (!changeSummary.isEmpty()) {
            auditService.record(actor, AuditAction.PLATFORM_SETTINGS_UPDATED, null, changeSummary.toString().trim());
        }

        return toDto(saved);
    }

    private PlatformSettings requireSettings() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Platform settings row is missing"));
    }

    private PlatformSettingsDto toDto(PlatformSettings settings) {
        return new PlatformSettingsDto(settings.isAutoApproveSignups(), settings.getPaymentInstructions());
    }
}
