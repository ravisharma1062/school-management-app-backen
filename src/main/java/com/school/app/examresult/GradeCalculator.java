package com.school.app.examresult;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GradeCalculator {

    private GradeCalculator() {
    }

    public static String computeGrade(BigDecimal marksObtained, BigDecimal maxMarks) {
        BigDecimal percentage = marksObtained
                .divide(maxMarks, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (percentage.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "A+";
        } else if (percentage.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "A";
        } else if (percentage.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "B";
        } else if (percentage.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "C";
        } else if (percentage.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "D";
        }
        return "F";
    }
}
