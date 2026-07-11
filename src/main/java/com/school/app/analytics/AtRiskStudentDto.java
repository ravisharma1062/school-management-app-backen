package com.school.app.analytics;

import java.util.UUID;

public record AtRiskStudentDto(
        UUID studentId,
        String studentName,
        String studentClass,
        String section,
        Double attendancePercentage,
        boolean attendanceAtRisk,
        boolean feeAtRisk,
        Integer maxDaysOverdue
) {
}
