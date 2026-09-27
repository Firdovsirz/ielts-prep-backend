package com.ieltsprep.mock;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MockDtos {

    private MockDtos() {}

    /**
     * @param state   NOT_STARTED, IN_PROGRESS or COMPLETED
     * @param grading NONE (auto-marked), PENDING, GRADED, FAILED or SELF_ASSESSED; bands stay hidden until all papers are done
     */
    @Schema(name = "MockStageView")
    public record StageView(String module, String label, int minutes, @Schema(nullable = true) Long sessionId, String state, String grading,
            @Schema(nullable = true) Double band, @Schema(nullable = true) Integer raw, @Schema(nullable = true) Integer max,
            @Schema(nullable = true) Integer timeUsedSeconds) {}

    @Schema(name = "MockQuestionTypeRow")
    public record TypeRow(String module, String questionType, int correct, int total) {}

    @Schema(name = "MockReport")
    public record Report(double overall, double target, Map<String, Double> bands, List<TypeRow> questionTypes,
            Map<String, Map<String, Integer>> writingCriteria, Map<String, Integer> speakingCriteria, List<String> strengths,
            List<String> weaknesses, String verdict) {}

    @Schema(name = "MockView")
    public record MockView(long id, String status, String stage, Instant startedAt, @Schema(nullable = true) Instant finishedAt,
            @Schema(nullable = true) Double overall, double targetBand, boolean gradingAvailable, List<StageView> stages,
            @Schema(nullable = true) Report report) {}

    @Schema(name = "MockSummary")
    public record Summary(long id, String status, String stage, Instant startedAt, @Schema(nullable = true) Instant finishedAt,
            @Schema(nullable = true) Double overall, @Schema(nullable = true) Double listening, @Schema(nullable = true) Double reading,
            @Schema(nullable = true) Double writing, @Schema(nullable = true) Double speaking) {}
}
