package com.school.app.billing;

import com.school.app.platform.PlatformUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/payments")
@RequiredArgsConstructor
@Tag(name = "Platform Payments")
public class PlatformPaymentController {

    private final PlatformPaymentService platformPaymentService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Payment claims awaiting verification, across every school, oldest first")
    public List<PlatformPaymentDto> listPending() {
        return platformPaymentService.listPending();
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Confirm a payment claim — extends the subscription's billing period and reactivates the school")
    public PlatformPaymentDto verify(
            @PathVariable UUID id, @Valid @RequestBody PaymentDecisionRequest request, @AuthenticationPrincipal PlatformUser currentUser) {
        return platformPaymentService.verify(id, request, currentUser);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Reject a payment claim")
    public PlatformPaymentDto reject(
            @PathVariable UUID id, @Valid @RequestBody PaymentDecisionRequest request, @AuthenticationPrincipal PlatformUser currentUser) {
        return platformPaymentService.reject(id, request, currentUser);
    }
}
