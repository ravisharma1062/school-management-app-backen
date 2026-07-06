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
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {
        return noticeService.list(role, currentUser, pageable);
    }
}
