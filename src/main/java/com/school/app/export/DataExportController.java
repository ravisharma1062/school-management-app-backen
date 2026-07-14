package com.school.app.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-export")
@RequiredArgsConstructor
@Tag(name = "Data Export")
public class DataExportController {

    private final DataExportService dataExportService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export this school's own data as a ZIP of CSVs (MT-6d)")
    public ResponseEntity<byte[]> export() {
        DataExportService.ExportResult result = dataExportService.exportCurrentSchool();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + result.filename())
                .contentType(MediaType.valueOf("application/zip"))
                .body(result.zipBytes());
    }
}
