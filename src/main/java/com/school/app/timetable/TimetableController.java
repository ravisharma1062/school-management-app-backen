package com.school.app.timetable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timetable")
@RequiredArgsConstructor
@Tag(name = "Timetable")
public class TimetableController {

    private final TimetableService timetableService;

    @GetMapping("/{studentClass}/{section}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "View the weekly timetable for a class/section")
    public List<TimetableDto> getByClassAndSection(
            @PathVariable String studentClass,
            @PathVariable String section,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return timetableService.getByClassAndSection(studentClass, section, includeArchived);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a timetable entry")
    public TimetableDto create(@Valid @RequestBody TimetableCreateRequest request) {
        return timetableService.create(request);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive (soft-delete) a timetable entry")
    public TimetableDto archive(@PathVariable UUID id) {
        return timetableService.archive(id);
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore a previously archived timetable entry")
    public TimetableDto restore(@PathVariable UUID id) {
        return timetableService.restore(id);
    }
}
