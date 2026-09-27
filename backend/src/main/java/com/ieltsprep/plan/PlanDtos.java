package com.ieltsprep.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PlanDtos {

    private PlanDtos() {}

    @Schema(name = "PlanTaskView")
    public record TaskView(long id, LocalDate date, String module, String action, @Schema(nullable = true) String variant, String title,
            @Schema(nullable = true) String details, int minutes, int priority, boolean done, @Schema(nullable = true) Instant doneAt) {}

    @Schema(name = "PlanDayView")
    public record DayView(LocalDate date, boolean today, boolean testDay, int minutesPlanned, int minutesDone, List<TaskView> tasks) {}

    @Schema(name = "PlanView")
    public record PlanView(LocalDate today, @Schema(nullable = true) LocalDate testDate, @Schema(nullable = true) Integer daysToTest,
            String phase, String phaseLabel, @Schema(nullable = true) String focus, String source,
            @Schema(nullable = true) Instant generatedAt, boolean aiAvailable, List<DayView> days) {}

    @Schema(name = "PlanToggleRequest")
    public record ToggleRequest(boolean done) {}
}
