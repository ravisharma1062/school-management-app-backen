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
        boolean oldValue = settings.isAutoApproveSignups();
        settings.setAutoApproveSignups(request.autoApproveSignups());
        PlatformSettings saved = platformSettingsRepository.save(settings);

        auditService.record(actor, AuditAction.PLATFORM_SETTINGS_UPDATED, null,
                "Changed auto-approve-signups from " + oldValue + " to " + request.autoApproveSignups());

        return toDto(saved);
    }

    private PlatformSettings requireSettings() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Platform settings row is missing"));
    }

    private PlatformSettingsDto toDto(PlatformSettings settings) {
        return new PlatformSettingsDto(settings.isAutoApproveSignups());
    }
}
