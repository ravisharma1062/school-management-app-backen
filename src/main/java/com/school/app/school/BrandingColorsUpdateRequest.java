package com.school.app.school;

import jakarta.validation.constraints.Pattern;

/** Either field may be omitted (left null) to leave that color unchanged. */
public record BrandingColorsUpdateRequest(
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex color like #4F46E5") String primaryColor,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex color like #4F46E5") String secondaryColor
) {
}
