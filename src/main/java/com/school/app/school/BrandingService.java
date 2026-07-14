package com.school.app.school;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.security.TenantContext;
import com.school.app.common.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads are open to every role in the school (the whole portal needs to theme itself, not just
 * admins) — only the writes are ADMIN + {@code @RequiresEntitlement(BRANDING)}-gated, enforced at
 * the controller. {@code School} isn't {@code @TenantId} — {@link TenantContext#get} is still safe
 * to use as the lookup key here since it's derived from the caller's own verified JWT, so this can
 * only ever resolve to the caller's own school.
 */
@Service
@RequiredArgsConstructor
public class BrandingService {

    private static final String LOGO_STORAGE_SUBDIRECTORY = "branding-logos";

    private final SchoolRepository schoolRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public BrandingDto getCurrent() {
        return toDto(requireCurrentSchool());
    }

    @Transactional
    public BrandingDto uploadLogo(MultipartFile file) {
        School school = requireCurrentSchool();
        school.setLogoKey(fileStorageService.store(file, LOGO_STORAGE_SUBDIRECTORY));
        school.setLogoContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        return toDto(schoolRepository.save(school));
    }

    @Transactional(readOnly = true)
    public StoredLogo downloadLogo() {
        School school = requireCurrentSchool();
        if (school.getLogoKey() == null) {
            throw new ResourceNotFoundException("This school has no logo set");
        }
        return new StoredLogo(fileStorageService.load(school.getLogoKey()), school.getLogoContentType());
    }

    @Transactional
    public BrandingDto updateColors(BrandingColorsUpdateRequest request) {
        School school = requireCurrentSchool();
        if (request.primaryColor() != null) {
            school.setPrimaryColor(request.primaryColor());
        }
        if (request.secondaryColor() != null) {
            school.setSecondaryColor(request.secondaryColor());
        }
        return toDto(schoolRepository.save(school));
    }

    private School requireCurrentSchool() {
        return schoolRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("School not found"));
    }

    private BrandingDto toDto(School school) {
        return new BrandingDto(school.getLogoKey() != null, school.getPrimaryColor(), school.getSecondaryColor());
    }

    public record StoredLogo(byte[] content, String contentType) {
    }
}
