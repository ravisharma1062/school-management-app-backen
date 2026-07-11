package com.school.app.leaverequest;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LeaveRequestCreateRequest(
        @NotNull LeaveType type,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        String reason
) {
}
