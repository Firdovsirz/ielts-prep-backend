package com.ieltsprep.speaking;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

/** What one speaking session covers: any of the three parts (a full test has all three, Part 3 linked to Part 2). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "SpeakingPlan")
public record SpeakingPlan(
        @Schema(nullable = true) SpeakingPrompts.Part1 part1,
        @Schema(nullable = true) SpeakingPrompts.Part2 part2,
        @Schema(nullable = true) SpeakingPrompts.Part3 part3) {}
