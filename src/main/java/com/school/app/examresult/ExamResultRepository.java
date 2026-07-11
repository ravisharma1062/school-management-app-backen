package com.school.app.examresult;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {

    List<ExamResult> findByStudentId(UUID studentId);

    List<ExamResult> findByStudentIdAndTerm(UUID studentId, String term);
}
