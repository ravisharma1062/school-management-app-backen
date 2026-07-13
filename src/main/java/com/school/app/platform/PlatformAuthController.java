package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/auth")
@RequiredArgsConstructor
@Tag(name = "Platform Auth")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate a platform operator; requires an MFA code once enrolled")
    public PlatformAuthResponse login(@Valid @RequestBody PlatformLoginRequest request) {
        return platformAuthService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid platform refresh token for a new token pair")
    public PlatformAuthResponse refresh(@Valid @RequestBody PlatformRefreshRequest request) {
        return platformAuthService.refresh(request);
    }

    @PostMapping("/mfa/enroll")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Generate a new TOTP secret to enroll; not active until /mfa/confirm")
    public MfaEnrollResponse enrollMfa(@AuthenticationPrincipal PlatformUser currentUser) {
        return platformAuthService.enrollMfa(currentUser);
    }

    @PostMapping("/mfa/confirm")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Confirm enrolment by proving a valid code for the enrolled secret")
    public void confirmMfa(@AuthenticationPrincipal PlatformUser currentUser, @Valid @RequestBody MfaConfirmRequest request) {
        platformAuthService.confirmMfa(currentUser, request);
    }
}
