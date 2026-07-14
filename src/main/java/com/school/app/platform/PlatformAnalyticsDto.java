package com.school.app.platform;

import com.school.app.school.SchoolStatus;

import java.util.Map;

public record PlatformAnalyticsDto(
        long totalSchools,
        Map<SchoolStatus, Long> schoolsByStatus,
        Map<PlanCode, Long> schoolsByPlan,
        /** MT-6c usage metering — aggregate totals across every provisioned school. */
        long totalActiveStudents,
        long totalEmailsSentThisMonth,
        long totalSmsSentThisMonth
) {
}
