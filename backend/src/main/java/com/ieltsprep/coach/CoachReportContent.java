package com.ieltsprep.coach;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Mirrors prompts/schemas/coach-report.schema.json. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "CoachReportContent")
public record CoachReportContent(
        String headline,
        String summary,
        String bandOutlook,
        List<String> wins,
        List<String> concerns,
        List<Priority> priorities,
        String encouragement) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(name = "CoachPriority")
    public record Priority(String module, String title, String detail) {}
}
