package com.school.app.attendance;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceDto(
        UUID id,
        UUID studentId,
        LocalDate date,
        AttendanceStatus status,
        UUID markedBy
) {
}
