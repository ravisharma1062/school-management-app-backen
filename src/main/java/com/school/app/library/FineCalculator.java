package com.school.app.library;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure fine-calculation logic — kept separate from I/O so it's directly unit-testable. */
public final class FineCalculator {

    public static final BigDecimal DAILY_FINE_RATE = new BigDecimal("5.00");

    private FineCalculator() {
    }

    /** Zero if returned on or before the due date, otherwise DAILY_FINE_RATE per day late. */
    public static BigDecimal calculateFine(LocalDate dueDate, LocalDate returnedDate) {
        if (!returnedDate.isAfter(dueDate)) {
            return BigDecimal.ZERO;
        }
        long daysLate = ChronoUnit.DAYS.between(dueDate, returnedDate);
        return DAILY_FINE_RATE.multiply(BigDecimal.valueOf(daysLate));
    }
}
