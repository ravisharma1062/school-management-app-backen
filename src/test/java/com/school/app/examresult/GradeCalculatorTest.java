package com.school.app.examresult;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GradeCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "95, 100, A+",
            "90, 100, A+",
            "85, 100, A",
            "80, 100, A",
            "75, 100, B",
            "70, 100, B",
            "65, 100, C",
            "60, 100, C",
            "55, 100, D",
            "50, 100, D",
            "49, 100, F",
            "0, 100, F",
            "27, 30, A+"
    })
    void computesExpectedGradeFromPercentage(String marks, String max, String expectedGrade) {
        String grade = GradeCalculator.computeGrade(new BigDecimal(marks), new BigDecimal(max));

        assertThat(grade).isEqualTo(expectedGrade);
    }
}
