package com.ieltsprep.export;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Export")
public class ExportController {

    private final ExportService export;

    public ExportController(ExportService export) {
        this.export = export;
    }

    /** All attempts, feedback, errors, cards, plans and API usage. format=json (default) or csv (ZIP of CSV files). */
    @GetMapping("/api/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "json") String format) {
        String stamp = LocalDate.now().toString();
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ielts-export-" + stamp + ".zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(export.csvZip());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ielts-export-" + stamp + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(export.json());
    }
}
