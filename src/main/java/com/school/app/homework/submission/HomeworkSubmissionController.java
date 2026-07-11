package com.school.app.homework.submission;

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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/homework")
@RequiredArgsConstructor
@Tag(name = "Homework Submissions")
public class HomeworkSubmissionController {

    private final HomeworkSubmissionService homeworkSubmissionService;

    @PostMapping(value = "/{homeworkId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "Submit a file for a piece of homework on behalf of a child")
    public HomeworkSubmissionDto submit(
            @PathVariable UUID homeworkId,
            @RequestParam UUID studentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return homeworkSubmissionService.submit(homeworkId, studentId, file, currentUser);
    }

    @PatchMapping("/submissions/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Grade a homework submission")
    public HomeworkSubmissionDto grade(@PathVariable UUID id, @Valid @RequestBody HomeworkSubmissionGradeRequest request) {
        return homeworkSubmissionService.grade(id, request);
    }

    @GetMapping("/{homeworkId}/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "List all submissions for a piece of homework")
    public List<HomeworkSubmissionDto> getByHomework(@PathVariable UUID homeworkId) {
        return homeworkSubmissionService.getByHomework(homeworkId);
    }

    @GetMapping("/submissions/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "List a student's homework submissions")
    public List<HomeworkSubmissionDto> getByStudent(@PathVariable UUID studentId, @AuthenticationPrincipal User currentUser) {
        return homeworkSubmissionService.getByStudent(studentId, currentUser);
    }

    @GetMapping("/submissions/{id}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "Download a submitted homework file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        HomeworkSubmissionService.StoredFile file = homeworkSubmissionService.downloadFile(id, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
