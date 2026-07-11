package com.school.app.notice;

import com.school.app.user.User;
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
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
@Tag(name = "Notices")
public class NoticeController {

    private final NoticeService noticeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Post a notice")
    public NoticeDto create(@Valid @RequestBody NoticeCreateRequest request, @AuthenticationPrincipal User currentUser) {
        return noticeService.create(request, currentUser);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "List notices visible to the current user, optionally filtered by target role")
    public Page<NoticeDto> list(
            @RequestParam(required = false) TargetRole role,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {
        return noticeService.list(role, currentUser, pageable, includeArchived);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive (soft-delete) a notice")
    public NoticeDto archive(@PathVariable UUID id) {
        return noticeService.archive(id);
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore a previously archived notice")
    public NoticeDto restore(@PathVariable UUID id) {
        return noticeService.restore(id);
    }
}
