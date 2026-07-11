package com.school.app.library;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookCreateRequest(
        @NotBlank String title,
        @NotBlank String author,
        String isbn,
        @NotNull @Min(1) Integer totalCopies
) {
}
