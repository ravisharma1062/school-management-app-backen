package com.school.app.common.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> fieldErrors,
        /** Machine-readable code clients can switch on, e.g. {@code SUBSCRIPTION_SUSPENDED}. Null for plain errors. */
        String code
) {
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null, null);
    }

    public ErrorResponse(Instant timestamp, int status, String error, String message, String path, List<String> fieldErrors) {
        this(timestamp, status, error, message, path, fieldErrors, null);
    }
}
