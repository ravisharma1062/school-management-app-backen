package com.school.app.platform;

import com.school.app.school.SchoolStatus;

import java.util.Map;

public record PlatformAnalyticsDto(
        long totalSchools,
        Map<SchoolStatus, Long> schoolsByStatus,
        Map<PlanCode, Long> schoolsByPlan
) {
}
