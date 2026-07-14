package com.school.app.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code periodEnd} must be after {@code periodStart} — checked in {@code BillingService}, not here (a cross-field rule). */
public record PaymentClaimCreateRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull PaymentMethod method,
        @NotBlank @Size(max = 100) String referenceNumber,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {
}
