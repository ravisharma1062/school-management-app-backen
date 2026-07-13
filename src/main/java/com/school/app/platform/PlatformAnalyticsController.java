package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/analytics")
@RequiredArgsConstructor
@Tag(name = "Platform Analytics")
public class PlatformAnalyticsController {

    private final PlatformAnalyticsService platformAnalyticsService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Active tenants, plan distribution, and status breakdown across every school")
    public PlatformAnalyticsDto get() {
        return platformAnalyticsService.get();
    }
}
