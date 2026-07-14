package com.school.app.school;

import com.school.app.platform.FeatureKey;
import com.school.app.platform.RequiresEntitlement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/branding")
@RequiredArgsConstructor
@Tag(name = "Branding")
public class BrandingController {

    private final BrandingService brandingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "This school's branding (logo presence + colors) — every role reads it to theme its own client")
    public BrandingDto getCurrent() {
        return brandingService.getCurrent();
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.BRANDING)
    @Operation(summary = "Upload this school's logo")
    public BrandingDto uploadLogo(@RequestParam("file") MultipartFile file) {
        return brandingService.uploadLogo(file);
    }

    @GetMapping("/logo")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "Download this school's logo image")
    public ResponseEntity<byte[]> downloadLogo() {
        BrandingService.StoredLogo logo = brandingService.downloadLogo();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType())).body(logo.content());
    }

    @PatchMapping("/colors")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.BRANDING)
    @Operation(summary = "Set this school's primary/secondary brand colors")
    public BrandingDto updateColors(@Valid @RequestBody BrandingColorsUpdateRequest request) {
        return brandingService.updateColors(request);
    }
}
