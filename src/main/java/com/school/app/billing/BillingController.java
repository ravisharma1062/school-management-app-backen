package com.school.app.billing;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing")
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/payment-instructions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "How to pay (bank details for NEFT, cheque/DD payee) — platform-wide, configured by the operator")
    public String getPaymentInstructions() {
        return billingService.getPaymentInstructions();
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Report a Demand Draft, Cheque, or NEFT payment made outside the app — starts PENDING_VERIFICATION")
    public PaymentClaimDto submit(@Valid @RequestBody PaymentClaimCreateRequest request, @AuthenticationPrincipal User currentUser) {
        return billingService.submit(request, currentUser);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "This school's own payment-claim history, newest first")
    public Page<PaymentClaimDto> getMyHistory(Pageable pageable) {
        return billingService.getMyHistory(pageable);
    }
}
