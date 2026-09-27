package com.ieltsprep.coach;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

public final class CoachDtos {

    private CoachDtos() {}

    @Schema(name = "CoachReportView")
    public record ReportView(long id, LocalDate weekStart, LocalDate weekEnd, Instant createdAt, String trigger, String generatedBy,
            CoachReportContent content, WeeklyStats stats) {}
}
