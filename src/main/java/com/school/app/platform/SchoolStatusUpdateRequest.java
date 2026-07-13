package com.school.app.platform;

import com.school.app.school.SchoolStatus;
import jakarta.validation.constraints.NotNull;

public record SchoolStatusUpdateRequest(
        @NotNull SchoolStatus status
) {
}
