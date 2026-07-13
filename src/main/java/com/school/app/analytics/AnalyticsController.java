package com.school.app.analytics;

import com.school.app.platform.FeatureKey;
import com.school.app.platform.RequiresEntitlement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/attendance")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.ANALYTICS)
    @Operation(summary = "Daily attendance trend, optionally filtered by class, over the last N days")
    public List<AttendanceTrendPointDto> attendanceTrend(
            @RequestParam(name = "class", required = false) String studentClass,
            @RequestParam(required = false, defaultValue = "30") int range) {
        return analyticsService.getAttendanceTrend(studentClass, range);
    }

    @GetMapping("/fees/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.ANALYTICS)
    @Operation(summary = "Fee collection summary, optionally filtered by class")
    public FeeSummaryDto feeSummary(@RequestParam(name = "class", required = false) String studentClass) {
        return analyticsService.getFeeSummary(studentClass);
    }

    @GetMapping("/at-risk-students")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.ANALYTICS)
    @Operation(summary = "Students with low attendance and/or significantly overdue fees")
    public List<AtRiskStudentDto> atRiskStudents() {
        return analyticsService.getAtRiskStudents();
    }
}
