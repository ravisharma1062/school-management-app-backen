package com.school.app.homework;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/homework")
@RequiredArgsConstructor
@Tag(name = "Homework")
public class HomeworkController {

    private final HomeworkService homeworkService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Post homework for a class/section")
    public HomeworkDto create(@Valid @RequestBody HomeworkCreateRequest request, @AuthenticationPrincipal User currentUser) {
        return homeworkService.create(request, currentUser);
    }

    @GetMapping("/{studentClass}/{section}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "List homework for a class/section (paginated)")
    public Page<HomeworkDto> getByClassAndSection(
            @PathVariable String studentClass, @PathVariable String section, Pageable pageable) {
        return homeworkService.getByClassAndSection(studentClass, section, pageable);
    }
}
