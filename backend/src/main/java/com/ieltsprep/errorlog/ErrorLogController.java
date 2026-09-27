package com.ieltsprep.errorlog;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/errors")
@Tag(name = "Error log")
public class ErrorLogController {

    @Schema(name = "ErrorEntryView")
    public record ErrorView(long id, long attemptId, String module, String type, String subtype,
            @Schema(nullable = true) String grammarArea, String original, @Schema(nullable = true) String correction,
            @Schema(nullable = true) String explanation, Instant createdAt, @Schema(nullable = true) Instant resolvedAt) {

        static ErrorView of(ErrorEntry e) {
            return new ErrorView(e.getId(), e.getAttemptId(), e.getModule().name(), e.getType(), e.getSubtype(), e.getGrammarArea(),
                    e.getOriginal(), e.getCorrection(), e.getExplanation(), e.getCreatedAt(), e.getResolvedAt());
        }
    }

    private final ErrorLogService log;

    public ErrorLogController(ErrorLogService log) {
        this.log = log;
    }

    @GetMapping("/summary")
    public List<ErrorAnalytics.SubtypeStats> summary(@RequestParam(required = false) String type) {
        return type == null ? log.summary() : log.summary(type);
    }

    @GetMapping
    public List<ErrorView> list(@RequestParam(required = false) String subtype) {
        return (subtype == null ? log.all() : log.bySubtype(subtype)).stream().map(ErrorView::of).toList();
    }
}
