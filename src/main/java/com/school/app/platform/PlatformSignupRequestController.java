package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/signup-requests")
@RequiredArgsConstructor
@Tag(name = "Platform Signup Requests")
public class PlatformSignupRequestController {

    private final PlatformSignupRequestService platformSignupRequestService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List signup requests")
    public Page<SignupRequestDto> list(Pageable pageable) {
        return platformSignupRequestService.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get one signup request")
    public SignupRequestDto get(@PathVariable UUID id) {
        return platformSignupRequestService.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Atomically provision a school + subscription + entitlements + admin invite")
    public ProvisionResultDto approve(
            @PathVariable UUID id,
            @Valid @RequestBody ProvisionApproveRequest request,
            @AuthenticationPrincipal PlatformUser currentUser) {
        return platformSignupRequestService.approve(id, request, currentUser);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Reject a signup request")
    public void reject(@PathVariable UUID id, @AuthenticationPrincipal PlatformUser currentUser) {
        platformSignupRequestService.reject(id, currentUser);
    }
}
