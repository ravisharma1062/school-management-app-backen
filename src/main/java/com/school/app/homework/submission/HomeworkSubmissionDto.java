package com.school.app.homework.submission;

import java.time.Instant;
import java.util.UUID;

public record HomeworkSubmissionDto(
        UUID id,
        UUID homeworkId,
        UUID studentId,
        String fileName,
        String contentType,
        HomeworkSubmissionStatus status,
        String teacherFeedback,
        String grade,
        Instant submittedAt
) {
}
