package com.ieltsprep.reading;

import com.ieltsprep.marking.QuestionMarker.QuestionResult;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ReadingDtos {

    private ReadingDtos() {}

    @Schema(name = "ReadingScope")
    public enum Scope {
        /** Full test: Passage 1 + 2 + 3, 40 questions, 60 minutes. */
        TEST,
        /** One passage, 20 minutes. */
        PASSAGE
    }

    @Schema(name = "ReadingPassageSummary")
    public record PassageSummary(long id, String title, @Schema(nullable = true) Integer difficulty,
            @Schema(nullable = true) String topic, @Schema(nullable = true) String cefr,
            @Schema(nullable = true) String questionTypes, int timesServed, String origin,
            @Schema(nullable = true) String sourceUrl) {}

    @Schema(name = "ReadingStartRequest")
    public record StartRequest(@NotNull SessionMode mode, @NotNull Scope scope, Integer difficulty, Long itemId) {}

    /** A passage as shown during the test: answers stripped, question numbers offset for the full test. */
    @Schema(name = "ReadingPassageView")
    public record PassageView(long itemId, int numberOffset, int questionCount, ReadingPassage passage,
            @Schema(nullable = true) String sourceUrl, @Schema(nullable = true) String licence) {}

    @Schema(name = "ReadingSessionView")
    public record SessionView(long sessionId, SessionMode mode, SessionKind kind, Instant startedAt,
            @Schema(nullable = true) Integer timeLimitSeconds, String status, List<PassageView> passages,
            @Schema(nullable = true) Long mockTestId) {}

    /**
     * @param answers           keyed by the question number shown on screen (1–40 in a full test)
     * @param secondsPerPassage time spent with each passage open, keyed by item id
     * @param flaggedWords      unfamiliar words the candidate flagged (added to the vocabulary deck)
     */
    @Schema(name = "ReadingSubmitRequest")
    public record SubmitRequest(Map<Integer, String> answers, Integer timeUsedSeconds, Map<Long, Integer> secondsPerPassage,
            List<FlaggedWord> flaggedWords) {}

    @Schema(name = "FlaggedWordRequest")
    public record FlaggedWord(String word, String sentence, Long itemId) {}

    @Schema(name = "ReadingPassageResult")
    public record PassageResult(long itemId, long attemptId, String title, int numberOffset, int raw, int max,
            @Schema(nullable = true) Integer secondsSpent, List<QuestionResult> questions) {}

    @Schema(name = "ReadingResultView")
    public record ResultView(long sessionId, SessionMode mode, SessionKind kind, int rawScore, int maxScore, double band,
            boolean bandIsEstimate, @Schema(nullable = true) Integer timeUsedSeconds,
            @Schema(nullable = true) Integer timeLimitSeconds, int blanks, Instant submittedAt,
            List<PassageResult> passages, List<PassageView> passageTexts) {}
}
