package com.ieltsprep.grammar;

import com.ieltsprep.errorlog.ErrorAnalytics;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class GrammarDtos {

    private GrammarDtos() {}

    @Schema(name = "GrammarAreaSummary")
    public record AreaSummary(String key, String name, String focus, @Schema(nullable = true) Double proficiency,
            @Schema(nullable = true) Double diagnosticScore, double accuracy, int questionsAnswered, int drillsDone,
            @Schema(nullable = true) Instant lastTested, @Schema(nullable = true) Long lessonItemId, int exerciseSets,
            int openErrors, boolean recommended) {}

    @Schema(name = "GrammarExerciseSetSummary")
    public record ExerciseSetSummary(long itemId, String exerciseType, String title, int itemCount, int timesServed,
            @Schema(nullable = true) Double lastScore) {}

    @Schema(name = "GrammarErrorExample")
    public record ErrorExample(long errorId, String original, @Schema(nullable = true) String correction,
            @Schema(nullable = true) String explanation, String subtype, Instant createdAt) {}

    @Schema(name = "GrammarAreaDetail")
    public record AreaDetail(AreaSummary area, @Schema(nullable = true) GrammarContent.Lesson lesson,
            List<ExerciseSetSummary> sets, List<ErrorExample> recentErrors) {}

    @Schema(name = "GrammarOverview")
    public record Overview(boolean diagnosticDone, @Schema(nullable = true) Long diagnosticInProgress, List<AreaSummary> areas,
            List<ErrorPracticeRow> errorPatterns) {}

    @Schema(name = "DiagnosticQuestionView")
    public record DiagnosticQuestionView(String id, String area, String areaName, int level, String prompt,
            List<GrammarContent.DiagnosticOption> options) {}

    @Schema(name = "DiagnosticView")
    public record DiagnosticView(long diagnosticId, String status, int asked, int total,
            @Schema(nullable = true) DiagnosticQuestionView question, @Schema(nullable = true) Map<String, Double> scores) {}

    @Schema(name = "DiagnosticAnswerRequest")
    public record DiagnosticAnswerRequest(String questionId, String answer) {}

    @Schema(name = "DiagnosticAnswerResult")
    public record DiagnosticAnswerResult(boolean correct, String correctAnswer, String explanation, DiagnosticView next) {}

    @Schema(name = "GrammarExerciseSessionView")
    public record ExerciseSessionView(long sessionId, String kind, String area, String areaName, String title,
            String instructions, String exerciseType, List<GrammarContent.ExerciseItem> items,
            @Schema(nullable = true) String ownSentence, String status) {}

    @Schema(name = "GrammarSubmitRequest")
    public record SubmitRequest(Map<String, String> answers) {}

    @Schema(name = "GrammarSelfAssessRequest")
    public record SelfAssessRequest(Map<String, Boolean> verdicts) {}

    /** method: EXACT (matched the key), AI (checked by Claude), SELF (you decide — no API key), PENDING. */
    @Schema(name = "GrammarItemResult")
    public record ItemResult(String id, String prompt, String given, @Schema(nullable = true) Boolean correct, String method,
            @Schema(nullable = true) String feedback, String modelAnswer, List<String> acceptedAnswers, String explanation,
            @Schema(nullable = true) String improvedVersion) {}

    @Schema(name = "GrammarResultView")
    public record ResultView(long sessionId, String kind, String area, String areaName, int score, int max, int pending,
            List<ItemResult> items, @Schema(nullable = true) String rule, @Schema(nullable = true) Double proficiency) {}

    @Schema(name = "GrammarErrorPracticeRow")
    public record ErrorPracticeRow(String subtype, @Schema(nullable = true) String area, @Schema(nullable = true) String areaName,
            int total, int unresolved, String trend, Instant lastSeen, boolean resolved, boolean drillReady, boolean retest,
            List<ErrorAnalytics.Example> examples) {}
}
