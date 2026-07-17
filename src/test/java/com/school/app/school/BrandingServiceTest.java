package com.school.app.school;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.security.TenantContext;
import com.school.app.common.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandingServiceTest {

    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private BrandingService brandingService;

    private UUID schoolId;
    private School school;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        TenantContext.set(schoolId);
        school = School.builder().id(schoolId).name("Springfield High").slug("springfield-high")
                .status(SchoolStatus.ACTIVE).build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getCurrentReflectsWhetherALogoIsSet() {
        school.setLogoKey("branding-logos/abc.png");
        school.setPrimaryColor("#4F46E5");
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        BrandingDto dto = brandingService.getCurrent();

        assertThat(dto.hasLogo()).isTrue();
        assertThat(dto.primaryColor()).isEqualTo("#4F46E5");
    }

    @Test
    void getCurrentThrowsWhenTheSchoolIsMissing() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandingService.getCurrent()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadLogoStoresTheFileAndPersistsTheReturnedKey() {
        MockMultipartFile file = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1, 2, 3});
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(fileStorageService.store(file, "branding-logos")).thenReturn("branding-logos/xyz.png");
        when(schoolRepository.save(school)).thenReturn(school);

        BrandingDto dto = brandingService.uploadLogo(file);

        assertThat(school.getLogoKey()).isEqualTo("branding-logos/xyz.png");
        assertThat(school.getLogoContentType()).isEqualTo("image/png");
        assertThat(dto.hasLogo()).isTrue();
    }

    @Test
    void uploadLogoFallsBackToOctetStreamWhenContentTypeIsUnknown() {
        MockMultipartFile file = new MockMultipartFile("logo", "logo", null, new byte[]{1});
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(fileStorageService.store(file, "branding-logos")).thenReturn("branding-logos/xyz");
        when(schoolRepository.save(school)).thenReturn(school);

        brandingService.uploadLogo(file);

        assertThat(school.getLogoContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void downloadLogoThrowsWhenNoLogoIsSet() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> brandingService.downloadLogo()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadLogoReturnsStoredBytesAndContentType() {
        school.setLogoKey("branding-logos/xyz.png");
        school.setLogoContentType("image/png");
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(fileStorageService.load("branding-logos/xyz.png")).thenReturn(new byte[]{9, 9});

        BrandingService.StoredLogo logo = brandingService.downloadLogo();

        assertThat(logo.content()).containsExactly(9, 9);
        assertThat(logo.contentType()).isEqualTo("image/png");
    }

    @Test
    void updateColorsOnlyChangesFieldsThatAreProvided() {
        school.setPrimaryColor("#111111");
        school.setSecondaryColor("#222222");
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(schoolRepository.save(school)).thenReturn(school);

        BrandingDto dto = brandingService.updateColors(new BrandingColorsUpdateRequest("#4F46E5", null));

        assertThat(school.getPrimaryColor()).isEqualTo("#4F46E5");
        assertThat(school.getSecondaryColor()).isEqualTo("#222222");
        assertThat(dto.primaryColor()).isEqualTo("#4F46E5");
        assertThat(dto.secondaryColor()).isEqualTo("#222222");
    }
}
