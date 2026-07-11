package com.school.app.leaverequest;

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
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
@Tag(name = "Leave Requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT')")
    @Operation(summary = "Submit a leave request")
    public LeaveRequestDto create(
            @Valid @RequestBody LeaveRequestCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return leaveRequestService.create(request, currentUser);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "List leave requests (admins see everyone's; teachers/parents see only their own)")
    public Page<LeaveRequestDto> list(
            @RequestParam(required = false) LeaveStatus status,
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {
        return leaveRequestService.list(status, currentUser, pageable);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve or reject a leave request")
    public LeaveRequestDto review(
            @PathVariable UUID id,
            @Valid @RequestBody LeaveRequestReviewRequest request,
            @AuthenticationPrincipal User currentUser) {
        return leaveRequestService.review(id, request, currentUser);
    }
}
