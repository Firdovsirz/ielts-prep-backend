package com.ieltsprep.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.band.BandCalculator;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.listening.ListeningDtos;
import com.ieltsprep.listening.ListeningService;
import com.ieltsprep.marking.QuestionMarker.QuestionResult;
import com.ieltsprep.reading.ReadingDtos;
import com.ieltsprep.reading.ReadingService;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.PracticeSessionRepository;
import com.ieltsprep.session.SessionMode;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.settings.SettingsService;
import com.ieltsprep.speaking.SpeakingDtos;
import com.ieltsprep.speaking.SpeakingService;
import com.ieltsprep.writing.WritingDtos;
import com.ieltsprep.writing.WritingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full mock test: Listening, Reading and Writing back to back in exam mode with real timings, then Speaking, and one
 * band report once Writing and Speaking are graded. Progress is derived from the four sessions, so a mock survives
 * reloads and can be resumed.
 */
@Service
public class MockService {

    public static final List<String> STAGES = List.of("LISTENING", "READING", "WRITING", "SPEAKING");

    private final MockTestRepository mocks;
    private final PracticeSessionRepository sessions;
    private final AttemptRepository attempts;
    private final ListeningService listening;
    private final ReadingService reading;
    private final WritingService writing;
    private final SpeakingService speaking;
    private final SettingsService settings;
    private final BandCalculator bands;
    private final ClaudeService claude;
    private final Clock clock;

    public MockService(MockTestRepository mocks, PracticeSessionRepository sessions, AttemptRepository attempts, ListeningService listening,
            ReadingService reading, WritingService writing, SpeakingService speaking, SettingsService settings, BandCalculator bands,
            ClaudeService claude, Clock clock) {
        this.mocks = mocks;
        this.sessions = sessions;
        this.attempts = attempts;
        this.listening = listening;
        this.reading = reading;
        this.writing = writing;
        this.speaking = speaking;
        this.settings = settings;
        this.bands = bands;
        this.claude = claude;
        this.clock = clock;
    }

    /** Resumes the mock in progress, or starts a new one. */
    @Transactional
    public MockDtos.MockView start() {
        MockTest m = mocks.findFirstByStatusOrderByStartedAtDesc("IN_PROGRESS").orElseGet(() -> {
            MockTest n = new MockTest();
            n.setStatus("IN_PROGRESS");
            n.setStage("LISTENING");
            n.setStartedAt(Instant.now(clock));
            return mocks.save(n);
        });
        return view(refresh(m));
    }

    @Transactional
    public MockDtos.MockView get(long id) {
        return view(refresh(mock(id)));
    }

    /** Creates (or returns) the exam-mode session for the current paper. */
    @Transactional
    public MockDtos.MockView startStage(long id) {
        MockTest m = refresh(mock(id));
        if (!"IN_PROGRESS".equals(m.getStatus()) || !STAGES.contains(m.getStage())) {
            throw ApiException.conflict("All four papers of this mock test have been taken");
        }
        if (sessionId(m, m.getStage()) == null) {
            long sid = switch (m.getStage()) {
                case "LISTENING" -> listening.start(new ListeningDtos.StartRequest(SessionMode.EXAM, ListeningDtos.Scope.TEST, null, null), m.getId())
                        .sessionId();
                case "READING" -> reading.start(new ReadingDtos.StartRequest(SessionMode.EXAM, ReadingDtos.Scope.TEST, null, null), m.getId())
                        .sessionId();
                case "WRITING" -> writing.start(new WritingDtos.StartRequest(SessionMode.EXAM, WritingDtos.Scope.TEST, null), m.getId())
                        .sessionId();
                default -> speaking.start(new SpeakingDtos.StartRequest(SessionMode.EXAM, SpeakingDtos.Scope.TEST, true), m.getId()).sessionId();
            };
            setSessionId(m, m.getStage(), sid);
        }
        return view(m);
    }

    @Transactional
    public MockDtos.MockView abandon(long id) {
        MockTest m = mock(id);
        if (!"COMPLETED".equals(m.getStatus())) {
            m.setStatus("ABANDONED");
            m.setFinishedAt(Instant.now(clock));
        }
        return view(m);
    }

    public List<MockDtos.Summary> list() {
        return mocks.findTop30ByOrderByStartedAtDesc().stream().map(m -> new MockDtos.Summary(m.getId(), m.getStatus(), m.getStage(),
                m.getStartedAt(), m.getFinishedAt(), d(m.getOverallBand()), d(m.getListeningBand()), d(m.getReadingBand()),
                d(m.getWritingBand()), d(m.getSpeakingBand()))).toList();
    }

    // ------------------------------------------------------------------ state

    /** Moves the mock on from its sessions: next paper, waiting for grading, or complete with the band report. */
    MockTest refresh(MockTest m) {
        if ("ABANDONED".equals(m.getStatus()) || "COMPLETED".equals(m.getStatus())) {
            return m;
        }
        for (String stage : STAGES) {
            Long sid = sessionId(m, stage);
            PracticeSession s = sid == null ? null : sessions.findById(sid).orElse(null);
            if (s == null || s.getStatus() != SessionStatus.COMPLETED) {
                m.setStage(stage);
                m.setStatus("IN_PROGRESS");
                return m;
            }
        }
        m.setListeningBand(band(m.getListeningSessionId()));
        m.setReadingBand(band(m.getReadingSessionId()));
        m.setWritingBand(band(m.getWritingSessionId()));
        m.setSpeakingBand(band(m.getSpeakingSessionId()));
        if (m.getFinishedAt() == null) {
            m.setFinishedAt(STAGES.stream().map(st -> sessions.findById(sessionId(m, st)).map(PracticeSession::getFinishedAt).orElse(null))
                    .filter(Objects::nonNull).max(Instant::compareTo).orElse(Instant.now(clock)));
        }
        if (m.getListeningBand() == null || m.getReadingBand() == null || m.getWritingBand() == null || m.getSpeakingBand() == null) {
            m.setStatus("GRADING");
            m.setStage("GRADING");
            return m;
        }
        double overall = bands.overallBand(m.getListeningBand().doubleValue(), m.getReadingBand().doubleValue(),
                m.getWritingBand().doubleValue(), m.getSpeakingBand().doubleValue());
        m.setOverallBand(BigDecimal.valueOf(overall));
        m.setStatus("COMPLETED");
        m.setStage("DONE");
        m.setReport(Json.write(report(m, overall)));
        return m;
    }

    MockDtos.MockView view(MockTest m) {
        boolean reveal = !"IN_PROGRESS".equals(m.getStatus());
        int transfer = settings.get().getListeningTransferMinutes();
        List<MockDtos.StageView> stages = new ArrayList<>();
        for (String stage : STAGES) {
            Long sid = sessionId(m, stage);
            PracticeSession s = sid == null ? null : sessions.findById(sid).orElse(null);
            String state = s == null ? "NOT_STARTED" : s.getStatus() == SessionStatus.COMPLETED ? "COMPLETED" : "IN_PROGRESS";
            int minutes = switch (stage) {
                case "LISTENING" -> 30 + transfer;
                case "SPEAKING" -> 14;
                default -> 60;
            };
            stages.add(new MockDtos.StageView(stage, label(stage), minutes, sid, state, grading(stage, s),
                    reveal && s != null ? d(s.getBandEstimate()) : null, reveal && s != null ? s.getRawScore() : null,
                    reveal && s != null ? s.getMaxScore() : null, s == null ? null : s.getTimeUsedSeconds()));
        }
        MockDtos.Report report = m.getReport() == null ? null : Json.read(m.getReport(), MockDtos.Report.class);
        return new MockDtos.MockView(m.getId(), m.getStatus(), m.getStage(), m.getStartedAt(), m.getFinishedAt(), d(m.getOverallBand()),
                settings.get().getTargetBand().doubleValue(), claude.isAvailable(), stages, report);
    }

    private String grading(String stage, PracticeSession s) {
        if (s == null || s.getStatus() != SessionStatus.COMPLETED) {
            return "NONE";
        }
        if (stage.equals("LISTENING") || stage.equals("READING")) {
            return "NONE";
        }
        List<Attempt> list = attempts.findBySessionIdOrderByIdAsc(s.getId());
        if (list.isEmpty()) {
            return "PENDING";
        }
        if (list.stream().anyMatch(a -> a.getStatus() == AttemptStatus.GRADING)) {
            return "PENDING";
        }
        if (list.stream().anyMatch(a -> a.getStatus() == AttemptStatus.GRADING_FAILED)) {
            return "FAILED";
        }
        if (list.stream().anyMatch(a -> a.getStatus() == AttemptStatus.SELF_ASSESSED)) {
            return "SELF_ASSESSED";
        }
        return "GRADED";
    }

    // ------------------------------------------------------------------ report

    MockDtos.Report report(MockTest m, double overall) {
        double target = settings.get().getTargetBand().doubleValue();
        Map<String, Double> b = new LinkedHashMap<>();
        b.put("LISTENING", m.getListeningBand().doubleValue());
        b.put("READING", m.getReadingBand().doubleValue());
        b.put("WRITING", m.getWritingBand().doubleValue());
        b.put("SPEAKING", m.getSpeakingBand().doubleValue());

        Map<String, int[]> types = new LinkedHashMap<>();
        for (Long sid : List.of(m.getListeningSessionId(), m.getReadingSessionId())) {
            for (Attempt a : attempts.findBySessionIdOrderByIdAsc(sid)) {
                if (a.getPerQuestion() == null) {
                    continue;
                }
                for (QuestionResult r : Json.read(a.getPerQuestion(), new TypeReference<List<QuestionResult>>() {})) {
                    int[] c = types.computeIfAbsent(a.getModule().name() + "|" + r.questionType().name(), k -> new int[2]);
                    c[0] += r.correct() ? 1 : 0;
                    c[1]++;
                }
            }
        }
        List<MockDtos.TypeRow> rows = types.entrySet().stream().map(e -> {
            String[] k = e.getKey().split("\\|");
            return new MockDtos.TypeRow(k[0], k[1], e.getValue()[0], e.getValue()[1]);
        }).sorted(Comparator.comparingDouble(r -> r.correct() / (double) r.total())).toList();

        Map<String, Map<String, Integer>> writingCriteria = new LinkedHashMap<>();
        for (Attempt a : attempts.findBySessionIdOrderByIdAsc(m.getWritingSessionId())) {
            if (a.getPerCriterionBands() != null) {
                writingCriteria.put(a.getTaskType().contains("TASK1") ? "TASK1" : "TASK2",
                        Json.read(a.getPerCriterionBands(), new TypeReference<Map<String, Integer>>() {}));
            }
        }
        Map<String, Integer> speakingCriteria = attempts.findBySessionIdOrderByIdAsc(m.getSpeakingSessionId()).stream()
                .filter(a -> a.getPerCriterionBands() != null).findFirst()
                .map(a -> Json.read(a.getPerCriterionBands(), new TypeReference<Map<String, Integer>>() {})).orElse(Map.of());

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        b.forEach((k, v) -> {
            if (v >= target) {
                strengths.add(name(k) + " " + fmt(v) + " — at or above your " + fmt(target) + " target.");
            }
        });
        rows.stream().filter(r -> r.total() >= 3 && r.correct() / (double) r.total() >= 0.8).limit(3)
                .forEach(r -> strengths.add(name(r.module()) + ": " + human(r.questionType()) + " " + r.correct() + "/" + r.total() + "."));
        String weakest = b.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
        if (b.get(weakest) < target) {
            weaknesses.add(name(weakest) + " " + fmt(b.get(weakest)) + " is your lowest paper — " + fmt(target - b.get(weakest))
                    + " below target.");
        }
        rows.stream().filter(r -> r.total() >= 2 && r.correct() / (double) r.total() < 0.6).limit(3)
                .forEach(r -> weaknesses.add(name(r.module()) + ": " + human(r.questionType()) + " only " + r.correct() + "/" + r.total() + "."));
        writingCriteria.forEach((task, crit) -> crit.entrySet().stream().min(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() < target).ifPresent(e -> weaknesses.add("Writing " + (task.equals("TASK1") ? "Task 1" : "Task 2") + ": "
                        + human(e.getKey()) + " " + e.getValue() + ".")));
        speakingCriteria.entrySet().stream().filter(e -> e.getValue() > 0).min(Map.Entry.comparingByValue()).filter(e -> e.getValue() < target)
                .ifPresent(e -> weaknesses.add("Speaking: " + human(e.getKey()) + " " + e.getValue() + "."));
        if (strengths.isEmpty()) {
            strengths.add("You completed all four papers under exam conditions — the most useful practice there is.");
        }
        double gap = target - overall;
        String verdict = "Overall band " + fmt(overall) + (gap <= 0 ? " — at or above your " + fmt(target) + " target."
                : " — " + fmt(gap) + " below your " + fmt(target) + " target. The quickest gain is " + name(weakest) + " ("
                        + fmt(b.get(weakest)) + ").");
        return new MockDtos.Report(overall, target, b, rows, writingCriteria, speakingCriteria, strengths, weaknesses, verdict);
    }

    // ------------------------------------------------------------------ helpers

    private MockTest mock(long id) {
        return mocks.findById(id).orElseThrow(() -> ApiException.notFound("Mock test " + id));
    }

    private BigDecimal band(Long sessionId) {
        return sessionId == null ? null : sessions.findById(sessionId).map(PracticeSession::getBandEstimate).orElse(null);
    }

    static Long sessionId(MockTest m, String stage) {
        return switch (stage) {
            case "LISTENING" -> m.getListeningSessionId();
            case "READING" -> m.getReadingSessionId();
            case "WRITING" -> m.getWritingSessionId();
            default -> m.getSpeakingSessionId();
        };
    }

    static void setSessionId(MockTest m, String stage, long id) {
        switch (stage) {
            case "LISTENING" -> m.setListeningSessionId(id);
            case "READING" -> m.setReadingSessionId(id);
            case "WRITING" -> m.setWritingSessionId(id);
            default -> m.setSpeakingSessionId(id);
        }
    }

    static String label(String stage) {
        return switch (stage) {
            case "LISTENING" -> "4 parts · 40 questions · audio plays once";
            case "READING" -> "3 passages · 40 questions";
            case "WRITING" -> "Task 1 (150 words) + Task 2 (250 words)";
            default -> "Parts 1–3 with the examiner";
        };
    }

    private static Double d(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    static String name(String module) {
        return module.charAt(0) + module.substring(1).toLowerCase(Locale.ROOT);
    }

    static String human(String key) {
        String s = key.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
