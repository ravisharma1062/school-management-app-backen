package com.school.app.timetable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/timetable")
@RequiredArgsConstructor
@Tag(name = "Timetable")
public class TimetableController {

    private final TimetableService timetableService;

    @GetMapping("/{studentClass}/{section}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "View the weekly timetable for a class/section")
    public List<TimetableDto> getByClassAndSection(@PathVariable String studentClass, @PathVariable String section) {
        return timetableService.getByClassAndSection(studentClass, section);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a timetable entry")
    public TimetableDto create(@Valid @RequestBody TimetableCreateRequest request) {
        return timetableService.create(request);
    }
}
