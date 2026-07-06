package com.school.app.examresult;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-results")
@RequiredArgsConstructor
@Tag(name = "Exam Results")
public class ExamResultController {

    private final ExamResultService examResultService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "Record an exam result (grade is computed server-side)")
    public ExamResultDto create(@Valid @RequestBody ExamResultCreateRequest request) {
        return examResultService.create(request);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT')")
    @Operation(summary = "View exam results for a student")
    public List<ExamResultDto> getByStudent(@PathVariable UUID studentId, @AuthenticationPrincipal User currentUser) {
        return examResultService.getByStudent(studentId, currentUser);
    }
}
