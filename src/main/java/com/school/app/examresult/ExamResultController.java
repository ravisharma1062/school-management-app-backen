package com.school.app.examresult;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping(value = "/student/{studentId}/report-card", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT')")
    @Operation(summary = "Download a student's report card as a PDF, optionally filtered by term")
    public ResponseEntity<byte[]> reportCard(
            @PathVariable UUID studentId,
            @RequestParam(required = false) String term,
            @AuthenticationPrincipal User currentUser) {
        byte[] pdf = examResultService.generateReportCard(studentId, term, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-card.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
