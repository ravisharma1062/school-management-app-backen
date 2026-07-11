package com.school.app.homework.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {

    List<HomeworkSubmission> findByHomeworkId(UUID homeworkId);

    List<HomeworkSubmission> findByStudentId(UUID studentId);

    Optional<HomeworkSubmission> findByHomeworkIdAndStudentId(UUID homeworkId, UUID studentId);

    boolean existsByHomeworkIdAndStudentId(UUID homeworkId, UUID studentId);
}
