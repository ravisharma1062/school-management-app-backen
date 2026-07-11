package com.school.app.leaverequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestDto(
        UUID id,
        UUID requesterId,
        LeaveType type,
        LocalDate fromDate,
        LocalDate toDate,
        String reason,
        LeaveStatus status,
        UUID reviewedBy,
        Instant createdAt
) {
}
