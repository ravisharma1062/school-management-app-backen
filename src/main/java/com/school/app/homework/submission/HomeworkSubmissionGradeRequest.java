package com.school.app.homework.submission;

import jakarta.validation.constraints.NotBlank;

public record HomeworkSubmissionGradeRequest(
        String teacherFeedback,
        @NotBlank String grade
) {
}
