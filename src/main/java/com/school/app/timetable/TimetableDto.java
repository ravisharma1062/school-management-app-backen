package com.school.app.timetable;

import java.time.DayOfWeek;
import java.util.UUID;

public record TimetableDto(
        UUID id,
        String studentClass,
        String section,
        DayOfWeek dayOfWeek,
        int period,
        String subject,
        UUID teacherId,
        boolean active
) {
}
