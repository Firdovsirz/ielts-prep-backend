package com.ieltsprep.speaking;

import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.band.BandCalculator;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.common.TextStats;
import com.ieltsprep.errorlog.ErrorLogService;
import com.ieltsprep.grading.GradingModels.CriterionBand;
import com.ieltsprep.grading.GradingModels.SpeakingGrade;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.PracticeSessionRepository;
import com.ieltsprep.settings.SettingsService;
import com.ieltsprep.vocab.VocabCapture;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grades a Speaking attempt from its transcripts. Pronunciation is only included in the band when an audio-analysis
 * service is configured; otherwise it is reported as not assessable and the band uses the other three criteria.
 */
@Component
public class SpeakingGrader {

    private static final Logger log = LoggerFactory.getLogger(SpeakingGrader.class);

    private final AttemptRepository attempts;
    private final SpeakingResponseRepository responses;
    private final PracticeSessionRepository sessions;
    private final ClaudeService claude;
    private final BandCalculator bands;
    private final ErrorLogService errorLog;
    private final VocabCapture vocab;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SpeakingGrader(AttemptRepository attempts, SpeakingResponseRepository responses, PracticeSessionRepository sessions,
            ClaudeService claude, BandCalculator bands, ErrorLogService errorLog, VocabCapture vocab, SettingsService settings,
            TransactionTemplate tx, Clock clock) {
        this.attempts = attempts;
        this.responses = responses;
        this.sessions = sessions;
        this.claude = claude;
        this.bands = bands;
        this.errorLog = errorLog;
        this.vocab = vocab;
        this.settings = settings;
        this.tx = tx;
        this.clock = clock;
    }

    public void grade(long attemptId) {
        Attempt attempt = attempts.findById(attemptId).orElseThrow();
        List<SpeakingResponse> list = responses.findBySessionIdOrderByIdAsc(attempt.getSessionId());
        SpeakingGrade grade;
        try {
            grade = claude.call(ClaudeCall.of("speaking-grade", Map.of(
                    "scope", attempt.getTaskType(),
                    "audio_analysis", "no — transcript only",
                    "target_band", settings.get().getTargetBand().stripTrailingZeros().toPlainString(),
                    "transcript", transcript(list)), SpeakingGrade.class).ref("grade speaking attempt " + attemptId));
        } catch (Exception e) {
            log.warn("Speaking grading {} failed: {}", attemptId, e.getMessage());
            tx.executeWithoutResult(s -> {
                Attempt a = attempts.findById(attemptId).orElseThrow();
                a.setStatus(AttemptStatus.GRADING_FAILED);
                a.setFeedback(Json.write(Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
                attempts.save(a);
            });
            return;
        }
        Map<String, Integer> criteria = new LinkedHashMap<>();
        for (CriterionBand c : grade.criteria()) {
            criteria.put(c.criterion(), Math.max(0, Math.min(9, c.band())));
        }
        List<Double> counted = criteria.entrySet().stream()
                .filter(e -> grade.pronunciationAssessable() || !e.getKey().equals("PRONUNCIATION"))
                .map(e -> e.getValue().doubleValue()).toList();
        double band = bands.criteriaBand(counted);
        tx.executeWithoutResult(s -> {
            Attempt a = attempts.findById(attemptId).orElseThrow();
            a.setStatus(AttemptStatus.GRADED);
            a.setFeedback(Json.write(grade));
            a.setPerCriterionBands(Json.write(criteria));
            a.setBandEstimate(BigDecimal.valueOf(band));
            a.setGradedAt(Instant.now(clock));
            attempts.save(a);
            PracticeSession session = sessions.findById(a.getSessionId()).orElseThrow();
            session.setBandEstimate(BigDecimal.valueOf(band));
            sessions.save(session);
            errorLog.record(a, grade.errors() == null ? List.of() : grade.errors());
        });
        if (grade.vocabularyUpgrades() != null) {
            grade.vocabularyUpgrades().forEach(v -> vocab.capture(v.better(), v.example(), "SPEAKING_FEEDBACK", "attempt " + attemptId));
        }
    }

    static String transcript(List<SpeakingResponse> list) {
        StringBuilder sb = new StringBuilder();
        int part = -1;
        for (SpeakingResponse r : list) {
            if (r.getPart() != part) {
                part = r.getPart();
                sb.append("\n=== PART ").append(part).append(" ===\n");
            }
            int words = TextStats.words(r.getTranscript());
            Integer secs = r.getDurationSeconds();
            sb.append("Examiner: ").append(r.getQuestion()).append("\n");
            sb.append("Candidate (").append(secs == null ? "?" : secs).append(" s, ").append(words).append(" words")
                    .append(secs != null && secs > 0 ? ", " + Math.round(words * 60.0 / secs) + " wpm" : "").append("): ")
                    .append(r.getTranscript() == null || r.getTranscript().isBlank() ? "(no speech captured)" : r.getTranscript()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
