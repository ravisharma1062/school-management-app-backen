package com.school.app.platform;

import java.util.UUID;

/**
 * MT-6c usage metering — platform-facing view of one school's consumption against its plan limits.
 */
public record SchoolUsageDto(
        UUID schoolId,
        long activeStudentCount,
        Integer maxStudentsLimit,
        long emailsSentThisMonth,
        long smsSentThisMonth
) {
}
