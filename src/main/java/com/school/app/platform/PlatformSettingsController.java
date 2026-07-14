package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/settings")
@RequiredArgsConstructor
@Tag(name = "Platform Settings")
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get platform-wide settings (MT-6f: auto-approve-signups)")
    public PlatformSettingsDto get() {
        return platformSettingsService.get();
    }

    @PatchMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Update platform-wide settings")
    public PlatformSettingsDto update(
            @Valid @RequestBody PlatformSettingsUpdateRequest request, @AuthenticationPrincipal PlatformUser currentUser) {
        return platformSettingsService.update(request, currentUser);
    }
}
