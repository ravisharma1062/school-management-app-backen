package com.school.app.fee;

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
@RequestMapping("/api/v1/fees")
@RequiredArgsConstructor
@Tag(name = "Fees")
public class FeeController {

    private final FeeService feeService;

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARENT')")
    @Operation(summary = "View fee status for a student")
    public List<FeeDto> getByStudent(@PathVariable UUID studentId, @AuthenticationPrincipal User currentUser) {
        return feeService.getByStudent(studentId, currentUser);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a fee record's paid amount/status")
    public FeeDto update(@PathVariable UUID id, @Valid @RequestBody FeeUpdateRequest request) {
        return feeService.update(id, request);
    }
}
