package com.ieltsprep.listening;

import com.ieltsprep.marking.QuestionMarker.QuestionResult;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ListeningDtos {

    private ListeningDtos() {}

    @Schema(name = "ListeningScope")
    public enum Scope { TEST, SECTION }

    @Schema(name = "ListeningSectionSummary")
    public record SectionSummary(long id, String title, @Schema(nullable = true) Integer section, @Schema(nullable = true) String topic,
            @Schema(nullable = true) String questionTypes, int timesServed, String origin) {}

    @Schema(name = "ListeningStartRequest")
    public record StartRequest(@NotNull SessionMode mode, @NotNull Scope scope, Integer section, Long itemId) {}

    /**
     * A section as served: answers stripped. The script is included because the browser speaks it (SpeechSynthesis);
     * the UI never displays it before submission.
     */
    @Schema(name = "ListeningSectionView")
    public record SectionView(long itemId, int numberOffset, int questionCount, ListeningSection section) {}

    @Schema(name = "ListeningSessionView")
    public record SessionView(long sessionId, SessionMode mode, SessionKind kind, Instant startedAt, String status,
            int readingSeconds, int transferMinutes, double speechRate, List<SectionView> sections) {}

    @Schema(name = "ListeningSubmitRequest")
    public record SubmitRequest(Map<Integer, String> answers, Integer timeUsedSeconds, Map<Long, Integer> replaysPerSection) {}

    @Schema(name = "ListeningSectionResult")
    public record SectionResult(long itemId, long attemptId, String title, int section, int numberOffset, int raw, int max,
            List<QuestionResult> questions) {}

    @Schema(name = "ListeningResultView")
    public record ResultView(long sessionId, SessionMode mode, SessionKind kind, int rawScore, int maxScore, double band,
            boolean bandIsEstimate, @Schema(nullable = true) Integer timeUsedSeconds, int blanks, Instant submittedAt,
            List<SectionResult> sections, List<SectionView> sectionTexts) {}
}
