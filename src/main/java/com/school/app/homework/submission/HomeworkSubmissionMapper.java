package com.school.app.homework.submission;

import org.springframework.stereotype.Component;

@Component
public class HomeworkSubmissionMapper {

    public HomeworkSubmissionDto toDto(HomeworkSubmission submission) {
        return new HomeworkSubmissionDto(
                submission.getId(),
                submission.getHomework().getId(),
                submission.getStudent().getId(),
                submission.getFileName(),
                submission.getContentType(),
                submission.getStatus(),
                submission.getTeacherFeedback(),
                submission.getGrade(),
                submission.getSubmittedAt()
        );
    }
}
