package com.school.app.attendance;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceMarkRequest(
        @NotNull UUID studentId,
        @NotNull LocalDate date,
        @NotNull AttendanceStatus status
) {
}
