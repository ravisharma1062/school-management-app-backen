package com.school.app.user;

import jakarta.validation.constraints.NotNull;

public record UserLanguageUpdateRequest(
        @NotNull LanguageCode preferredLanguage
) {
}
