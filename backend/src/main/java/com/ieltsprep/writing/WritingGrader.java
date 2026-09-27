package com.ieltsprep.writing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.band.BandCalculator;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.errorlog.ErrorLogService;
import com.ieltsprep.grading.GradingModels.CriterionBand;
import com.ieltsprep.grading.GradingModels.WritingGrade;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.PracticeSessionRepository;
import com.ieltsprep.settings.SettingsService;
import com.ieltsprep.vocab.VocabCapture;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grades one Writing attempt with Claude (writing-grade prompt), stores bands/feedback, pushes tagged errors to the
 * error log and vocabulary upgrades to the deck, and updates the session's Writing band.
 */
@Component
public class WritingGrader {

    private static final Logger log = LoggerFactory.getLogger(WritingGrader.class);

    private final AttemptRepository attempts;
    private final ItemRepository items;
    private final PracticeSessionRepository sessions;
    private final ClaudeService claude;
    private final BandCalculator bands;
    private final ErrorLogService errorLog;
    private final VocabCapture vocab;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public WritingGrader(AttemptRepository attempts, ItemRepository items, PracticeSessionRepository sessions, ClaudeService claude,
            BandCalculator bands, ErrorLogService errorLog, VocabCapture vocab, SettingsService settings, TransactionTemplate tx,
            Clock clock) {
        this.attempts = attempts;
        this.items = items;
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
        Item item = items.findById(attempt.getItemId()).orElseThrow();
        String text = String.valueOf(Json.read(attempt.getMyAnswers(), new TypeReference<Map<String, Object>>() {}).get("text"));
        WritingGrade grade;
        try {
            grade = claude.call(ClaudeCall.of("writing-grade", vars(item, attempt, text), WritingGrade.class).ref("grade writing attempt " + attemptId));
        } catch (Exception e) {
            log.warn("Grading attempt {} failed: {}", attemptId, e.getMessage());
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
        double band = bands.criteriaBand(criteria.values().stream().map(Integer::doubleValue).toList());
        tx.executeWithoutResult(s -> {
            Attempt a = attempts.findById(attemptId).orElseThrow();
            a.setStatus(AttemptStatus.GRADED);
            a.setFeedback(Json.write(grade));
            a.setPerCriterionBands(Json.write(criteria));
            a.setBandEstimate(BigDecimal.valueOf(band));
            a.setGradedAt(Instant.now(clock));
            attempts.save(a);
            errorLog.record(a, grade.errors() == null ? List.of() : grade.errors());
            updateSessionBand(a.getSessionId());
        });
        if (grade.vocabularyUpgrades() != null) {
            grade.vocabularyUpgrades().forEach(v -> vocab.capture(v.better(), v.example(), "WRITING_FEEDBACK", "attempt " + attemptId));
        }
    }

    /** Session band: Task 1 + 2×Task 2 when both are graded, otherwise the single task's band. */
    void updateSessionBand(long sessionId) {
        PracticeSession s = sessions.findById(sessionId).orElseThrow();
        List<Attempt> list = attempts.findBySessionIdOrderByIdAsc(sessionId);
        Map<Integer, Double> byTask = new HashMap<>();
        for (Attempt a : list) {
            if (a.getBandEstimate() != null) {
                byTask.put(TaskType.valueOf(a.getTaskType()) == TaskType.WRITING_TASK2 ? 2 : 1, a.getBandEstimate().doubleValue());
            }
        }
        Double band = byTask.size() == 2 ? bands.writingBand(byTask.get(1), byTask.get(2))
                : byTask.values().stream().findFirst().orElse(null);
        s.setBandEstimate(band == null ? null : BigDecimal.valueOf(band));
        sessions.save(s);
    }

    private Map<String, Object> vars(Item item, Attempt attempt, String text) {
        Map<String, Object> vars = new HashMap<>();
        TaskType type = item.getTaskType();
        String figure = "";
        String promptText;
        List<String> keyFeatures;
        String label;
        switch (type) {
            case WRITING_TASK1_ACADEMIC -> {
                WritingPrompts.Task1Academic t = item.contentAs(WritingPrompts.Task1Academic.class);
                label = "IELTS Academic Writing Task 1 (" + t.chartType() + ")";
                promptText = t.prompt();
                keyFeatures = t.keyFeatures();
                figure = "Figure data (the candidate saw this rendered as a " + t.chartType().toLowerCase() + "):\n"
                        + Json.pretty(Map.of("figure_title", t.figureTitle(), "units", t.units(), "x_label", t.xLabel(),
                                "y_label", t.yLabel(), "categories", t.categories(), "series", t.series(),
                                "process_steps", t.processSteps(), "maps", t.maps()));
            }
            case WRITING_TASK1_GENERAL -> {
                WritingPrompts.Task1General t = item.contentAs(WritingPrompts.Task1General.class);
                label = "IELTS General Training Writing Task 1 (" + t.letterType() + " letter)";
                promptText = t.prompt();
                keyFeatures = t.keyFeatures();
            }
            default -> {
                WritingPrompts.Task2 t = item.contentAs(WritingPrompts.Task2.class);
                label = "IELTS Writing Task 2 (" + t.essayType() + " essay)";
                promptText = t.prompt();
                keyFeatures = t.keyFeatures();
            }
        }
        vars.put("task_label", label);
        vars.put("prompt_text", promptText);
        vars.put("figure_block", figure);
        vars.put("key_features", keyFeatures.stream().map(k -> "- " + k).collect(Collectors.joining("\n")));
        vars.put("word_count", attempt.getWordCount());
        vars.put("minutes_used", attempt.getDurationSeconds() == null ? "an unrecorded number of" : String.valueOf(Math.round(attempt.getDurationSeconds() / 60.0)));
        vars.put("minutes_allowed", WritingService.minutes(type));
        vars.put("response_text", text);
        vars.put("target_band", settings.get().getTargetBand().stripTrailingZeros().toPlainString());
        return vars;
    }
}
