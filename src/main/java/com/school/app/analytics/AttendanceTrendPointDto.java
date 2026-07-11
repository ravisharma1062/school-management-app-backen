package com.school.app.analytics;

import java.time.LocalDate;

public record AttendanceTrendPointDto(
        LocalDate date,
        long presentCount,
        long absentCount,
        long lateCount,
        long excusedCount,
        double attendancePercentage
) {
}
