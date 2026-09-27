package com.ieltsprep.speaking;

import com.ieltsprep.grading.GradingModels.SpeakingGrade;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SpeakingDtos {

    private SpeakingDtos() {}

    @Schema(name = "SpeakingScope")
    public enum Scope { TEST, PART1, PART2, PART3 }

    @Schema(name = "SpeakingStartRequest")
    public record StartRequest(@NotNull SessionMode mode, @NotNull Scope scope, Boolean conversational) {}

    @Schema(name = "SpeakingResponseView")
    public record ResponseView(long id, int part, int questionIndex, String question, @Schema(nullable = true) String transcript,
            @Schema(nullable = true) Integer durationSeconds, boolean hasAudio) {}

    @Schema(name = "SpeakingSessionView")
    public record SessionView(long sessionId, SessionMode mode, SessionKind kind, String status, Instant startedAt, SpeakingPlan plan,
            boolean conversational, boolean examinerAvailable, String transcription, List<ResponseView> responses) {}

    @Schema(name = "SpeakingResultView")
    public record ResultView(long sessionId, SessionMode mode, SessionKind kind, String status, @Schema(nullable = true) Long attemptId,
            @Schema(nullable = true) Double band, @Schema(nullable = true) Map<String, Integer> criteriaBands,
            @Schema(nullable = true) SpeakingGrade grade, @Schema(nullable = true) String error, int words,
            @Schema(nullable = true) Double wordsPerMinute, List<ResponseView> responses, SpeakingPlan plan) {}
}
