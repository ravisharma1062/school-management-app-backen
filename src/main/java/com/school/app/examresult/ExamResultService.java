package com.school.app.examresult;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamResultService {

    private final ExamResultRepository examResultRepository;
    private final StudentRepository studentRepository;
    private final ExamResultMapper examResultMapper;

    public ExamResultDto create(ExamResultCreateRequest request) {
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + request.studentId() + " not found"));

        String grade = GradeCalculator.computeGrade(request.marksObtained(), request.maxMarks());

        ExamResult examResult = ExamResult.builder()
                .student(student)
                .subject(request.subject())
                .examName(request.examName())
                .marksObtained(request.marksObtained())
                .maxMarks(request.maxMarks())
                .grade(grade)
                .term(request.term())
                .build();

        return examResultMapper.toDto(examResultRepository.save(examResult));
    }

    public List<ExamResultDto> getByStudent(UUID studentId, User currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's exam results");
        }

        return examResultRepository.findByStudentId(studentId).stream()
                .map(examResultMapper::toDto)
                .toList();
    }
}
