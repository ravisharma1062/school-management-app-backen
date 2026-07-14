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
@RequestMapping("/api/v1/platform/schools")
@RequiredArgsConstructor
@Tag(name = "Platform Schools")
public class PlatformSchoolController {

    private final PlatformSchoolService platformSchoolService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List every provisioned school")
    public Page<SchoolAdminDto> list(Pageable pageable) {
        return platformSchoolService.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get one school")
    public SchoolAdminDto get(@PathVariable UUID id) {
        return platformSchoolService.get(id);
    }

    @GetMapping("/{id}/usage")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get a school's current usage against its plan limits")
    public SchoolUsageDto getUsage(@PathVariable UUID id) {
        return platformSchoolService.getUsage(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Suspend, reactivate, or cancel a school")
    public SchoolAdminDto updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody SchoolStatusUpdateRequest request,
            @AuthenticationPrincipal PlatformUser currentUser) {
        return platformSchoolService.updateStatus(id, request.status(), currentUser);
    }
}
