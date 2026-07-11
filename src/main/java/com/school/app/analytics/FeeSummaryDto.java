package com.school.app.analytics;

import java.math.BigDecimal;

public record FeeSummaryDto(
        BigDecimal totalDue,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        double collectionPercentage,
        long pendingCount,
        long partialCount,
        long paidCount,
        long overdueCount
) {
}
