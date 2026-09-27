package com.ieltsprep.grammar;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Mirrors the grammar-lesson, grammar-exercise-set, grammar-diagnostic-question and grammar-error-drill schemas. */
public final class GrammarContent {

    private GrammarContent() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record KeyRule(String rule, String example) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CommonMistake(String wrong, String right, String why) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record IeltsExample(String context, String sentence) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Lesson(
            String area,
            String title,
            String summary,
            String explanation,
            List<KeyRule> keyRules,
            List<CommonMistake> commonMistakes,
            List<IeltsExample> ieltsExamples) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExerciseItem(
            String id,
            String prompt,
            List<String> options,
            List<String> acceptedAnswers,
            String modelAnswer,
            String checkMode,
            String explanation) {

        public ExerciseItem withoutAnswers() {
            return new ExerciseItem(id, prompt, options, List.of(), "", checkMode, "");
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExerciseSet(
            String area,
            String exerciseType,
            String title,
            String instructions,
            List<ExerciseItem> items) {

        public ExerciseSet withoutAnswers() {
            return new ExerciseSet(area, exerciseType, title, instructions,
                    items.stream().map(ExerciseItem::withoutAnswers).toList());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DiagnosticOption(String key, String text) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DiagnosticQuestion(
            String id,
            String area,
            int level,
            String prompt,
            List<DiagnosticOption> options,
            String answer,
            String explanation) {}

    /** Error-driven drill built from one of the candidate's own sentences plus three fresh items. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ErrorDrill(
            String subtype,
            String area,
            String rule,
            String ownSentence,
            List<String> ownSentenceCorrections,
            String ownSentenceExplanation,
            List<ExerciseItem> freshItems) {}
}
