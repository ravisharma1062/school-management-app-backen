package com.school.app.analytics;

/** Pure threshold logic for flagging at-risk students — kept separate from I/O so it's directly unit-testable. */
public final class AtRiskThresholds {

    public static final int ATTENDANCE_WINDOW_DAYS = 30;
    public static final double ATTENDANCE_THRESHOLD_PERCENT = 75.0;
    public static final int FEE_OVERDUE_DAYS_THRESHOLD = 7;

    private AtRiskThresholds() {
    }

    /** True when the student's attended (present/late) share of marked days is below the threshold. */
    public static boolean isAttendanceAtRisk(long attendedCount, long totalMarkedCount) {
        if (totalMarkedCount == 0) {
            return false;
        }
        double percentage = 100.0 * attendedCount / totalMarkedCount;
        return percentage < ATTENDANCE_THRESHOLD_PERCENT;
    }

    /** True when the student has an unpaid fee overdue by at least the threshold number of days. */
    public static boolean isFeeAtRisk(Integer maxDaysOverdue) {
        return maxDaysOverdue != null && maxDaysOverdue >= FEE_OVERDUE_DAYS_THRESHOLD;
    }

    public static double attendancePercentage(long attendedCount, long totalMarkedCount) {
        return totalMarkedCount == 0 ? 0.0 : 100.0 * attendedCount / totalMarkedCount;
    }
}
