package com.school.app.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtRiskThresholdsTest {

    @Test
    void attendanceBelow75PercentIsAtRisk() {
        assertThat(AtRiskThresholds.isAttendanceAtRisk(7, 10)).isTrue();
    }

    @Test
    void attendanceAtExactly75PercentIsNotAtRisk() {
        assertThat(AtRiskThresholds.isAttendanceAtRisk(15, 20)).isFalse();
    }

    @Test
    void attendanceAbove75PercentIsNotAtRisk() {
        assertThat(AtRiskThresholds.isAttendanceAtRisk(9, 10)).isFalse();
    }

    @Test
    void noAttendanceRecordsIsNotFlaggedAsAtRisk() {
        assertThat(AtRiskThresholds.isAttendanceAtRisk(0, 0)).isFalse();
    }

    @Test
    void feeOverdueAtExactlyThresholdIsAtRisk() {
        assertThat(AtRiskThresholds.isFeeAtRisk(7)).isTrue();
    }

    @Test
    void feeOverdueBelowThresholdIsNotAtRisk() {
        assertThat(AtRiskThresholds.isFeeAtRisk(6)).isFalse();
    }

    @Test
    void noOverdueFeeIsNotAtRisk() {
        assertThat(AtRiskThresholds.isFeeAtRisk(null)).isFalse();
    }

    @Test
    void attendancePercentageIsComputedCorrectly() {
        assertThat(AtRiskThresholds.attendancePercentage(3, 4)).isEqualTo(75.0);
    }

    @Test
    void attendancePercentageWithNoRecordsIsZero() {
        assertThat(AtRiskThresholds.attendancePercentage(0, 0)).isEqualTo(0.0);
    }
}
