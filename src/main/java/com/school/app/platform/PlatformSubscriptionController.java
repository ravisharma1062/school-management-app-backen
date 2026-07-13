package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Platform Subscriptions")
public class PlatformSubscriptionController {

    private final PlatformSubscriptionService platformSubscriptionService;

    @GetMapping("/{schoolId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get a school's subscription and resolved entitlements")
    public SubscriptionAdminDto get(@PathVariable UUID schoolId) {
        return platformSubscriptionService.get(schoolId);
    }

    @PatchMapping("/{schoolId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Change a school's plan; recomputes its entitlements to the new plan's defaults")
    public SubscriptionAdminDto update(
            @PathVariable UUID schoolId,
            @Valid @RequestBody SubscriptionUpdateRequest request,
            @AuthenticationPrincipal PlatformUser currentUser) {
        return platformSubscriptionService.updatePlan(schoolId, request.planCode(), currentUser);
    }
}
