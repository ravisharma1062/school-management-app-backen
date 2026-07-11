package com.school.app.library;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookIssueCreateRequest(
        @NotNull UUID bookId,
        @NotNull UUID studentId
) {
}
