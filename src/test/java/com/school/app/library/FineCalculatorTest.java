package com.school.app.library;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FineCalculatorTest {

    @Test
    void returningOnTheDueDateIncursNoFine() {
        LocalDate dueDate = LocalDate.of(2026, 1, 15);

        assertThat(FineCalculator.calculateFine(dueDate, dueDate)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void returningBeforeTheDueDateIncursNoFine() {
        LocalDate dueDate = LocalDate.of(2026, 1, 15);

        assertThat(FineCalculator.calculateFine(dueDate, dueDate.minusDays(3))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void returningLateChargesTheDailyRatePerDayLate() {
        LocalDate dueDate = LocalDate.of(2026, 1, 15);

        assertThat(FineCalculator.calculateFine(dueDate, dueDate.plusDays(3)))
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void returningOneDayLateChargesASingleDaysFine() {
        LocalDate dueDate = LocalDate.of(2026, 1, 15);

        assertThat(FineCalculator.calculateFine(dueDate, dueDate.plusDays(1)))
                .isEqualByComparingTo(FineCalculator.DAILY_FINE_RATE);
    }
}
