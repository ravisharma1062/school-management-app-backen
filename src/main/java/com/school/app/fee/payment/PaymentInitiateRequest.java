package com.school.app.fee.payment;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull UUID feeId
) {
}
