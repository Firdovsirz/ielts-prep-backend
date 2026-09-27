package com.ieltsprep.grammar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.errorlog.ErrorAnalytics;
import com.ieltsprep.errorlog.ErrorEntry;
import com.ieltsprep.errorlog.ErrorLogService;
import com.ieltsprep.grammar.GrammarDtos.AreaDetail;
import com.ieltsprep.grammar.GrammarDtos.AreaSummary;
import com.ieltsprep.grammar.GrammarDtos.ErrorExample;
import com.ieltsprep.grammar.GrammarDtos.ErrorPracticeRow;
import com.ieltsprep.grammar.GrammarDtos.ExerciseSetSummary;
import com.ieltsprep.grammar.GrammarDtos.Overview;
import com.ieltsprep.content.Skill;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Curriculum, per-area progress and the error-pattern overview for the Grammar module. */
@Service
public class GrammarService {

    private final GrammarTaxonomy taxonomy;
    private final GrammarProgressRepository progress;
    private final GrammarDiagnosticRepository diagnostics;
    private final ItemRepository items;
    private final AttemptRepository attempts;
    private final ErrorLogService errorLog;
    private final Clock clock;

    public GrammarService(GrammarTaxonomy taxonomy, GrammarProgressRepository progress, GrammarDiagnosticRepository diagnostics,
            ItemRepository items, AttemptRepository attempts, ErrorLogService errorLog, Clock clock) {
        this.taxonomy = taxonomy;
        this.progress = progress;
        this.diagnostics = diagnostics;
        this.items = items;
        this.attempts = attempts;
        this.errorLog = errorLog;
        this.clock = clock;
    }

    public Overview overview() {
        boolean done = diagnostics.findFirstByStatusOrderByIdDesc("COMPLETED").isPresent();
        Long inProgress = diagnostics.findFirstByStatusOrderByIdDesc("IN_PROGRESS").map(GrammarDiagnostic::getId).orElse(null);
        return new Overview(done, inProgress, areas(), errorPatterns());
    }

    public List<AreaSummary> areas() {
        Map<String, Long> openErrors = errorLog.summary("grammar").stream().filter(s -> s.area() != null)
                .collect(Collectors.groupingBy(ErrorAnalytics.SubtypeStats::area, Collectors.summingLong(ErrorAnalytics.SubtypeStats::unresolved)));
        Map<String, Long> setCounts = items.findByTaskTypeAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_EXERCISE, VerificationStatus.VERIFIED)
                .stream().collect(Collectors.groupingBy(Item::getVariant, Collectors.counting()));
        Map<String, Long> lessons = items.findByTaskTypeAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_LESSON, VerificationStatus.VERIFIED)
                .stream().collect(Collectors.toMap(Item::getVariant, Item::getId, (a, b) -> b));
        List<AreaSummary> list = taxonomy.areas().stream().map(a -> {
            GrammarProgress p = progress.findByArea(a.key()).orElse(null);
            return new AreaSummary(a.key(), a.name(), a.focus(),
                    p == null || (p.getQuestionsAnswered() == 0 && p.getDiagnosticScore() == null) ? null : p.getProficiency(),
                    p == null ? null : p.getDiagnosticScore(), p == null ? 0 : p.getAccuracy(), p == null ? 0 : p.getQuestionsAnswered(),
                    p == null ? 0 : p.getDrillsDone(), p == null ? null : p.getLastTested(), lessons.get(a.key()),
                    setCounts.getOrDefault(a.key(), 0L).intValue(), openErrors.getOrDefault(a.key(), 0L).intValue(), false);
        }).toList();
        // recommend the three weakest areas once there is evidence: open errors weigh heavily, then low proficiency
        List<String> recommended = list.stream()
                .filter(s -> s.proficiency() != null || s.openErrors() > 0)
                .sorted(Comparator.comparingDouble((AreaSummary s) -> -s.openErrors() * 15 + (s.proficiency() == null ? 55 : s.proficiency())))
                .limit(3).map(AreaSummary::key).toList();
        return list.stream().map(s -> new AreaSummary(s.key(), s.name(), s.focus(), s.proficiency(), s.diagnosticScore(), s.accuracy(),
                s.questionsAnswered(), s.drillsDone(), s.lastTested(), s.lessonItemId(), s.exerciseSets(), s.openErrors(),
                recommended.contains(s.key()))).toList();
    }

    @Transactional
    public AreaDetail area(String key) {
        taxonomy.area(key).orElseThrow(() -> ApiException.notFound("Grammar area " + key));
        AreaSummary summary = areas().stream().filter(a -> a.key().equals(key)).findFirst().orElseThrow();
        GrammarContent.Lesson lesson = items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_LESSON, key,
                VerificationStatus.VERIFIED).stream().reduce((a, b) -> b).map(i -> i.contentAs(GrammarContent.Lesson.class)).orElse(null);
        if (lesson != null) {
            GrammarProgress p = progressFor(key);
            p.setLessonViewedAt(Instant.now(clock));
            progress.save(p);
        }
        Map<Long, Double> lastScores = lastScores();
        List<ExerciseSetSummary> sets = items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_EXERCISE, key,
                VerificationStatus.VERIFIED).stream().map(i -> {
                    GrammarContent.ExerciseSet set = i.contentAs(GrammarContent.ExerciseSet.class);
                    return new ExerciseSetSummary(i.getId(), set.exerciseType(), set.title(), set.items().size(), i.getTimesServed(),
                            lastScores.get(i.getId()));
                }).toList();
        List<ErrorExample> errors = errorLog.all().stream().filter(e -> key.equals(e.getGrammarArea())).limit(12)
                .map(e -> new ErrorExample(e.getId(), e.getOriginal(), e.getCorrection(), e.getExplanation(), e.getSubtype(), e.getCreatedAt()))
                .toList();
        return new AreaDetail(summary, lesson, sets, errors);
    }

    public List<ErrorPracticeRow> errorPatterns() {
        Instant now = Instant.now(clock);
        Map<String, List<ErrorEntry>> bySubtype = errorLog.all().stream().filter(e -> "grammar".equals(e.getType()))
                .collect(Collectors.groupingBy(ErrorEntry::getSubtype));
        return errorLog.summary("grammar").stream().map(s -> {
            boolean ready = items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_ERROR_DRILL, s.subtype(),
                    VerificationStatus.VERIFIED).stream().anyMatch(i -> i.getTimesServed() == 0);
            Instant lastDrilled = bySubtype.getOrDefault(s.subtype(), List.of()).stream().map(ErrorEntry::getLastDrilledAt)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            boolean retest = s.resolved() && (lastDrilled == null || Duration.between(lastDrilled, now).toDays() >= 14);
            return new ErrorPracticeRow(s.subtype(), s.area(), s.area() == null ? null : taxonomy.area(s.area()).map(GrammarTaxonomy.Area::name).orElse(null),
                    s.total(), s.unresolved(), s.trend().name(), s.lastSeen(), s.resolved(), ready, retest, s.examples());
        }).toList();
    }

    /** Records a marked exercise/drill/diagnostic result into the area's progress. */
    @Transactional
    public double recordResult(String area, int correct, int total, boolean drill) {
        GrammarProgress p = progressFor(area);
        boolean first = p.getQuestionsAnswered() == 0 && p.getDiagnosticScore() == null;
        p.setQuestionsAnswered(p.getQuestionsAnswered() + total);
        p.setCorrectAnswers(p.getCorrectAnswers() + correct);
        p.setAccuracy(p.getQuestionsAnswered() == 0 ? 0 : (double) p.getCorrectAnswers() / p.getQuestionsAnswered());
        double session = total == 0 ? 0 : 100.0 * correct / total;
        p.setProficiency(Math.round((first ? session : 0.75 * p.getProficiency() + 0.25 * session) * 10) / 10.0);
        if (drill) {
            p.setDrillsDone(p.getDrillsDone() + 1);
        }
        p.setLastTested(Instant.now(clock));
        progress.save(p);
        return p.getProficiency();
    }

    @Transactional
    public void recordDiagnostic(Map<String, Double> scores) {
        scores.forEach((area, score) -> {
            GrammarProgress p = progressFor(area);
            p.setDiagnosticScore(score);
            p.setProficiency(p.getQuestionsAnswered() == 0 ? score : Math.round((0.5 * p.getProficiency() + 0.5 * score) * 10) / 10.0);
            p.setLastTested(Instant.now(clock));
            progress.save(p);
        });
    }

    GrammarProgress progressFor(String area) {
        return progress.findByArea(area).orElseGet(() -> {
            GrammarProgress p = new GrammarProgress();
            p.setArea(area);
            return progress.save(p);
        });
    }

    private Map<Long, Double> lastScores() {
        Map<Long, Double> out = new HashMap<>();
        for (Attempt a : attempts.findByModuleOrderBySubmittedAtAsc(Skill.GRAMMAR)) {
            if (a.getItemId() != null && a.getMaxScore() != null && a.getMaxScore() > 0 && a.getRawScore() != null) {
                out.put(a.getItemId(), Math.round(100.0 * a.getRawScore() / a.getMaxScore() * 10) / 10.0);
            }
        }
        return out;
    }

    /** Full text a graded attempt was written/spoken with (for locating the candidate's own sentence). */
    public static String attemptText(Attempt a) {
        if (a == null || a.getMyAnswers() == null) {
            return "";
        }
        Map<String, Object> answers = Json.read(a.getMyAnswers(), new TypeReference<Map<String, Object>>() {});
        if (answers == null) {
            return "";
        }
        if (answers.get("text") != null) {
            return String.valueOf(answers.get("text"));
        }
        Object responses = answers.get("responses");
        if (responses instanceof List<?> list) {
            return list.stream().map(r -> r instanceof Map<?, ?> m ? String.valueOf(m.get("transcript")) : "").collect(Collectors.joining("\n"));
        }
        return "";
    }
}
