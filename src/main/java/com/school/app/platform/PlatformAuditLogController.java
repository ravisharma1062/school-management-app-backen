package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Platform Audit Log")
public class PlatformAuditLogController {

    private final PlatformAuditLogService platformAuditLogService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Every cross-tenant platform action, newest first")
    public Page<AuditLogDto> list(Pageable pageable) {
        return platformAuditLogService.list(pageable);
    }
}
