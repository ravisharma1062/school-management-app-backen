package com.school.app.fee.payment;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Create a gateway order for an outstanding fee, ready for client-side checkout")
    public PaymentInitiateResponse initiate(
            @Valid @RequestBody PaymentInitiateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return paymentService.initiate(request, currentUser);
    }

    @PostMapping("/webhook")
    @Operation(summary = "Razorpay payment webhook (signature-verified, not JWT-authenticated)")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawPayload, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARENT')")
    @Operation(summary = "Look up a payment's status (parents may only view their own child's payments)")
    public PaymentDto getById(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return paymentService.getById(id, currentUser);
    }
}
