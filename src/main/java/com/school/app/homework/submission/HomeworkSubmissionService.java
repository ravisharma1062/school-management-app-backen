package com.school.app.homework.submission;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.DuplicateResourceException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.storage.FileStorageService;
import com.school.app.homework.Homework;
import com.school.app.homework.HomeworkRepository;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HomeworkSubmissionService {

    private static final String STORAGE_SUBDIRECTORY = "homework-submissions";

    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkRepository homeworkRepository;
    private final StudentRepository studentRepository;
    private final FileStorageService fileStorageService;
    private final HomeworkSubmissionMapper mapper;

    public HomeworkSubmissionDto submit(UUID homeworkId, UUID studentId, MultipartFile file, User currentUser) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ResourceNotFoundException("Homework with id " + homeworkId + " not found"));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Parents may only submit homework on behalf of their own children");
        }

        if (!homework.getStudentClass().equals(student.getStudentClass()) || !homework.getSection().equals(student.getSection())) {
            throw new BadRequestException("This homework was not assigned to this student's class");
        }

        if (submissionRepository.existsByHomeworkIdAndStudentId(homeworkId, studentId)) {
            throw new DuplicateResourceException("This student has already submitted this homework");
        }

        String fileKey = fileStorageService.store(file, STORAGE_SUBDIRECTORY);

        HomeworkSubmission submission = HomeworkSubmission.builder()
                .homework(homework)
                .student(student)
                .fileKey(fileKey)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "submission")
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .status(HomeworkSubmissionStatus.SUBMITTED)
                .build();

        return mapper.toDto(submissionRepository.save(submission));
    }

    public HomeworkSubmissionDto grade(UUID submissionId, HomeworkSubmissionGradeRequest request) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission with id " + submissionId + " not found"));

        submission.setTeacherFeedback(request.teacherFeedback());
        submission.setGrade(request.grade());
        submission.setStatus(HomeworkSubmissionStatus.GRADED);

        return mapper.toDto(submissionRepository.save(submission));
    }

    public List<HomeworkSubmissionDto> getByHomework(UUID homeworkId) {
        if (!homeworkRepository.existsById(homeworkId)) {
            throw new ResourceNotFoundException("Homework with id " + homeworkId + " not found");
        }
        return submissionRepository.findByHomeworkId(homeworkId).stream().map(mapper::toDto).toList();
    }

    public List<HomeworkSubmissionDto> getByStudent(UUID studentId, User currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's submissions");
        }

        return submissionRepository.findByStudentId(studentId).stream().map(mapper::toDto).toList();
    }

    public StoredFile downloadFile(UUID submissionId, User currentUser) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission with id " + submissionId + " not found"));

        if (currentUser.getRole() == Role.PARENT) {
            Student student = studentRepository.findById(submission.getStudent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Parents may only download their own child's submissions");
            }
        }

        byte[] content = fileStorageService.load(submission.getFileKey());
        return new StoredFile(content, submission.getFileName(), submission.getContentType());
    }

    public record StoredFile(byte[] content, String fileName, String contentType) {
    }
}
