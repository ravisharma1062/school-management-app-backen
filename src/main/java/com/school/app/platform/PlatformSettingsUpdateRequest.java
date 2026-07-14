package com.school.app.platform;

import jakarta.validation.constraints.Size;

/** Partial-update (PATCH) semantics — a null field means "leave unchanged", not "clear it". */
public record PlatformSettingsUpdateRequest(
        Boolean autoApproveSignups,
        @Size(max = 4000) String paymentInstructions
) {
}
