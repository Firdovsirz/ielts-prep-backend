package com.ieltsprep.writing;

import com.ieltsprep.grading.GradingModels.WritingGrade;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WritingDtos {

    private WritingDtos() {}

    @Schema(name = "WritingScope")
    public enum Scope { TASK1, TASK2, TEST }

    @Schema(name = "WritingPromptSummary")
    public record PromptSummary(long id, String taskType, @Schema(nullable = true) String variant, @Schema(nullable = true) String title,
            @Schema(nullable = true) String topic, int timesServed, String origin) {}

    @Schema(name = "WritingStartRequest")
    public record StartRequest(@NotNull SessionMode mode, @NotNull Scope scope, Long itemId) {}

    /** One task as shown in the exam: exactly one of the three prompt fields is set. */
    @Schema(name = "WritingTaskView")
    public record TaskView(long itemId, String taskType, int task, int minutes, int minWords,
            @Schema(nullable = true) WritingPrompts.Task1Academic task1Academic,
            @Schema(nullable = true) WritingPrompts.Task1General task1General,
            @Schema(nullable = true) WritingPrompts.Task2 task2) {}

    @Schema(name = "WritingSessionView")
    public record SessionView(long sessionId, SessionMode mode, SessionKind kind, Instant startedAt,
            @Schema(nullable = true) Integer timeLimitSeconds, String status, List<TaskView> tasks) {}

    @Schema(name = "WritingResponseRequest")
    public record ResponseRequest(long itemId, String text, Integer secondsSpent) {}

    @Schema(name = "WritingSubmitRequest")
    public record SubmitRequest(List<ResponseRequest> responses, Integer timeUsedSeconds) {}

    @Schema(name = "WritingAttemptView")
    public record AttemptView(long attemptId, long itemId, String taskType, int task, String text, int wordCount, String status,
            @Schema(nullable = true) Double band, @Schema(nullable = true) Map<String, Integer> criteriaBands,
            @Schema(nullable = true) WritingGrade grade, @Schema(nullable = true) String error,
            @Schema(nullable = true) Integer secondsSpent, Instant submittedAt, @Schema(nullable = true) Instant gradedAt) {}

    @Schema(name = "WritingResultView")
    public record ResultView(long sessionId, SessionMode mode, SessionKind kind, String status,
            @Schema(nullable = true) Double writingBand, boolean bandIsEstimate, @Schema(nullable = true) Integer timeUsedSeconds,
            @Schema(nullable = true) Integer timeLimitSeconds, List<AttemptView> attempts, List<TaskView> tasks) {}
}
