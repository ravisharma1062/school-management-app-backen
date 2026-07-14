package com.school.app.examresult;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.NotificationEventType;
import com.school.app.common.notification.NotificationService;
import com.school.app.common.security.TenantContext;
import com.school.app.common.storage.FileStorageService;
import com.school.app.platform.EntitlementService;
import com.school.app.platform.FeatureKey;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
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
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final FileStorageService fileStorageService;
    private final EntitlementService entitlementService;

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

        ExamResultDto dto = examResultMapper.toDto(examResultRepository.save(examResult));

        if (student.getParent() != null) {
            userRepository.findById(student.getParent().getId()).ifPresent(parent ->
                    notificationService.notify(
                            NotificationEventType.EXAM_RESULT_PUBLISHED,
                            parent,
                            "New exam result for " + student.getName(),
                            student.getName() + " scored " + request.marksObtained() + "/" + request.maxMarks()
                                    + " (" + grade + ") in " + request.subject() + " — " + request.examName() + "."));
        }

        return dto;
    }

    public List<ExamResultDto> getByStudent(UUID studentId, User currentUser) {
        Student student = requireVisibleStudent(studentId, currentUser);

        return examResultRepository.findByStudentId(studentId).stream()
                .map(examResultMapper::toDto)
                .toList();
    }

    public byte[] generateReportCard(UUID studentId, String term, User currentUser) {
        Student student = requireVisibleStudent(studentId, currentUser);

        List<ExamResult> results = term != null && !term.isBlank()
                ? examResultRepository.findByStudentIdAndTerm(studentId, term)
                : examResultRepository.findByStudentId(studentId);

        List<ExamResultDto> dtos = results.stream().map(examResultMapper::toDto).toList();

        School school = schoolRepository.findById(TenantContext.get()).orElse(null);
        byte[] logoBytes = null;
        String primaryColor = null;
        // Branding on the PDF follows the school's CURRENT entitlement, not just whether a logo/color
        // happens to still be stored — a downgraded school's report cards quietly stop being branded
        // rather than needing an explicit "clear branding" step.
        if (school != null && entitlementService.isEntitled(FeatureKey.BRANDING)) {
            if (school.getLogoKey() != null) {
                logoBytes = fileStorageService.load(school.getLogoKey());
            }
            primaryColor = school.getPrimaryColor();
        }

        return ReportCardGenerator.generate(
                school != null ? school.getName() : null, logoBytes, primaryColor,
                student.getName(), student.getStudentClass(), student.getSection(), dtos);
    }

    private Student requireVisibleStudent(UUID studentId, User currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's exam results");
        }

        return student;
    }
}
