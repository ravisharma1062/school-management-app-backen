package com.school.app.attendance;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Mark (or update) attendance for a student on a given date")
    public AttendanceDto mark(@Valid @RequestBody AttendanceMarkRequest request, @AuthenticationPrincipal User currentUser) {
        return attendanceService.mark(request, currentUser);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT')")
    @Operation(summary = "View attendance history for a student")
    public List<AttendanceDto> getByStudent(@PathVariable UUID studentId, @AuthenticationPrincipal User currentUser) {
        return attendanceService.getByStudent(studentId, currentUser);
    }

    @GetMapping("/class/{studentClass}/{section}/{date}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "View attendance for a whole class/section on a given date")
    public List<AttendanceDto> getByClassSectionDate(
            @PathVariable String studentClass,
            @PathVariable String section,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.getByClassSectionDate(studentClass, section, date);
    }
}
