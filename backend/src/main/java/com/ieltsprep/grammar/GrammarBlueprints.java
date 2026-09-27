package com.ieltsprep.grammar;

import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.common.Sentences;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.errorlog.ErrorEntry;
import com.ieltsprep.errorlog.ErrorLogService;
import com.ieltsprep.generation.Blueprint;
import com.ieltsprep.generation.GenerationPlan;
import com.ieltsprep.generation.GenerationRequest;
import com.ieltsprep.generation.VerificationReport;
import com.ieltsprep.generation.Verdict;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Generation blueprints for grammar exercise sets (buffered) and error-driven drills (on demand). */
@Configuration
public class GrammarBlueprints {

    static final List<String> EXERCISE_TYPES = List.of("GAP_FILL", "ERROR_CORRECTION", "SENTENCE_TRANSFORMATION",
            "SENTENCE_COMBINING", "UPGRADE_SENTENCE", "FREE_WRITING");

    @Bean
    Blueprint grammarExerciseBlueprint(ItemRepository items, GrammarTaxonomy taxonomy, @Lazy GrammarService grammar,
            ErrorLogService errorLog) {
        return new Blueprint() {
            @Override
            public TaskType type() {
                return TaskType.GRAMMAR_EXERCISE;
            }

            @Override
            public List<String> bufferVariants() {
                return java.util.Collections.singletonList(null);
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                // explicit area, else the weakest recommended area, else the area with the fewest exercise sets
                String area = request.variant() != null ? request.variant()
                        : grammar.areas().stream().filter(GrammarDtos.AreaSummary::recommended).findFirst()
                                .or(() -> grammar.areas().stream().min(Comparator.comparingInt(GrammarDtos.AreaSummary::exerciseSets)))
                                .map(GrammarDtos.AreaSummary::key).orElseThrow();
                GrammarTaxonomy.Area a = taxonomy.require(area);
                Map<String, Long> used = items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_EXERCISE, area,
                        VerificationStatus.VERIFIED).stream().collect(Collectors.groupingBy(Item::getQuestionTypes, Collectors.counting()));
                String type = EXERCISE_TYPES.stream().min(Comparator.comparingLong(t -> used.getOrDefault(t, 0L))).orElseThrow();
                String errors = errorLog.all().stream().filter(e -> area.equals(e.getGrammarArea())).limit(6)
                        .map(e -> "\"" + e.getOriginal() + "\" → \"" + e.getCorrection() + "\"").collect(Collectors.joining("; "));
                Map<String, Object> vars = new HashMap<>();
                vars.put("area", area);
                vars.put("area_name", a.name());
                vars.put("area_focus", a.focus());
                vars.put("exercise_type", type);
                vars.put("recent_errors", errors.isBlank() ? "none recorded yet" : errors);
                return new GenerationPlan("grammar-exercise-generate", vars, null, "Original content", area + " " + type);
            }

            @Override
            public String verifyPrompt() {
                return "grammar-exercise-verify";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return Map.of("exercise_text", ExerciseText.set((GrammarContent.ExerciseSet) content));
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return judgeItems(((GrammarContent.ExerciseSet) content).items(), report);
            }
        };
    }

    @Bean
    Blueprint grammarErrorDrillBlueprint(GrammarTaxonomy taxonomy, ErrorLogService errorLog, AttemptRepository attempts) {
        return new Blueprint() {
            @Override
            public TaskType type() {
                return TaskType.GRAMMAR_ERROR_DRILL;
            }

            @Override
            public List<String> bufferVariants() {
                return List.of();
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                String subtype = request.variant();
                List<ErrorEntry> rows = errorLog.bySubtype(subtype);
                if (rows.isEmpty()) {
                    throw new IllegalArgumentException("No errors logged for " + subtype);
                }
                ErrorEntry latest = rows.getFirst();
                String area = latest.getGrammarArea() != null ? latest.getGrammarArea() : taxonomy.areaForSubtype(subtype).orElseThrow();
                String text = GrammarService.attemptText(attempts.findById(latest.getAttemptId()).orElse(null));
                Map<String, Object> vars = new HashMap<>();
                vars.put("subtype", subtype);
                vars.put("area", area);
                vars.put("area_name", taxonomy.require(area).name());
                vars.put("own_sentence", Sentences.containing(text, latest.getOriginal()));
                vars.put("original", latest.getOriginal());
                vars.put("correction", latest.getCorrection());
                vars.put("explanation", latest.getExplanation());
                vars.put("other_examples", rows.stream().skip(1).limit(4)
                        .map(e -> "- \"" + e.getOriginal() + "\" → \"" + e.getCorrection() + "\"").collect(Collectors.joining("\n")));
                return new GenerationPlan("grammar-error-drill-generate", vars, null, "Original content", "Drill " + subtype);
            }

            @Override
            public String verifyPrompt() {
                return "grammar-exercise-verify";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return Map.of("exercise_text", ExerciseText.drill((GrammarContent.ErrorDrill) content));
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return judgeItems(((GrammarContent.ErrorDrill) content).freshItems(), report);
            }
        };
    }

    /** EXACT items must match the verifier's blind answer; open items are only checked for blocker issues. */
    static Verdict judgeItems(List<GrammarContent.ExerciseItem> items, VerificationReport report) {
        Map<String, VerificationReport.BlindAnswer> blind = report.blindAnswers() == null ? Map.of()
                : report.blindAnswers().stream().collect(Collectors.toMap(b -> b.id().trim(), Function.identity(), (a, b) -> a));
        List<String> reasons = new ArrayList<>();
        for (GrammarContent.ExerciseItem i : items) {
            if (!"EXACT".equals(i.checkMode())) {
                continue;
            }
            VerificationReport.BlindAnswer b = blind.get(i.id());
            if (b == null) {
                reasons.add("Item " + i.id() + ": verifier gave no answer");
            } else if (!GrammarMarker.matches(b.answer(), i.acceptedAnswers())) {
                reasons.add("Item " + i.id() + ": an independent solver answered '" + b.answer() + "' but the key only accepts "
                        + i.acceptedAnswers() + " — add the missing correct answer or make the item unambiguous");
            }
        }
        report.blockers().forEach(i -> reasons.add("Item " + i.id() + " " + i.kind() + ": " + i.detail()));
        if (!report.passed() && reasons.isEmpty()) {
            reasons.add("Verifier verdict FAIL: " + report.summary());
        }
        return reasons.isEmpty() ? Verdict.pass(report.summary()) : Verdict.fail(reasons, report.summary());
    }
}
