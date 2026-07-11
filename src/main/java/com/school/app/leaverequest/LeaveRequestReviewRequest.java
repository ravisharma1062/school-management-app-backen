package com.school.app.leaverequest;

import jakarta.validation.constraints.NotNull;

public record LeaveRequestReviewRequest(
        @NotNull LeaveStatus status
) {
}
