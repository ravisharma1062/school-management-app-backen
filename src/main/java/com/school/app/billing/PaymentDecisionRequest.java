package com.school.app.billing;

import jakarta.validation.constraints.Size;

public record PaymentDecisionRequest(
        @Size(max = 2000) String notes
) {
}
