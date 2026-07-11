package com.school.app.student;

import java.util.List;

public record BulkImportResult(
        int totalRows,
        int successCount,
        int failureCount,
        List<RowError> errors
) {
    public record RowError(int row, String message) {
    }
}
