package com.school.app.student;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Students")
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "List students (paginated), optionally filtered by name, roll number, or class")
    public Page<StudentDto> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String rollNo,
            @RequestParam(required = false) String studentClass,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            Pageable pageable) {
        return studentService.list(name, rollNo, studentClass, includeArchived, pageable);
    }

    @GetMapping("/my-children")
    @PreAuthorize("hasRole('PARENT')")
    @Operation(summary = "List the authenticated parent's own children")
    public List<StudentDto> myChildren(@AuthenticationPrincipal User currentUser) {
        return studentService.getMyChildren(currentUser);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a student")
    public StudentDto create(@Valid @RequestBody StudentCreateRequest request) {
        return studentService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "Get a student by id (parents may only view their own child)")
    public StudentDto getById(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return studentService.getById(id, currentUser);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a student")
    public StudentDto update(@PathVariable UUID id, @Valid @RequestBody StudentUpdateRequest request) {
        return studentService.update(id, request);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive (soft-delete) a student")
    public StudentDto archive(@PathVariable UUID id) {
        return studentService.archive(id);
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore a previously archived student")
    public StudentDto restore(@PathVariable UUID id) {
        return studentService.restore(id);
    }

    @PostMapping(value = "/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bulk-create students from a CSV file (name,rollNo,studentClass,section,dob[,parentEmail])")
    public BulkImportResult bulkImport(@RequestParam("file") MultipartFile file) {
        return studentService.bulkImport(file);
    }
}
