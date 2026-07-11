package com.school.app.library;

import java.util.UUID;

public record BookDto(
        UUID id,
        String title,
        String author,
        String isbn,
        boolean hasCoverImage,
        int totalCopies,
        int availableCopies
) {
}
