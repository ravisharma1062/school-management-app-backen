package com.school.app.examresult;

import org.springframework.stereotype.Component;

@Component
public class ExamResultMapper {

    public ExamResultDto toDto(ExamResult examResult) {
        return new ExamResultDto(
                examResult.getId(),
                examResult.getStudent().getId(),
                examResult.getSubject(),
                examResult.getExamName(),
                examResult.getMarksObtained(),
                examResult.getMaxMarks(),
                examResult.getGrade(),
                examResult.getTerm()
        );
    }
}
