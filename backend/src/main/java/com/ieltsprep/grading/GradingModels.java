package com.ieltsprep.grading;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Records shared by the Writing and Speaking grading schemas. */
public final class GradingModels {

    private GradingModels() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CriterionBand(String criterion, int band, String justification, List<String> strengths, List<String> weaknesses) {}

    /** One tagged error: type grammar|vocabulary|coherence|task, subtype from the taxonomy, verbatim original. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaggedError(String type, String subtype, String original, String correction, String explanation) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VocabUpgrade(String original, String better, String example) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WritingGrade(
            List<CriterionBand> criteria,
            List<TaggedError> errors,
            List<String> improvements,
            List<VocabUpgrade> vocabularyUpgrades,
            String wordCountComment,
            String overallComment,
            String modelAnswer) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ModelSpokenAnswer(String question, String answer) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SpeakingGrade(
            List<CriterionBand> criteria,
            boolean pronunciationAssessable,
            List<TaggedError> errors,
            List<String> improvements,
            List<VocabUpgrade> vocabularyUpgrades,
            String overallComment,
            List<ModelSpokenAnswer> modelAnswers) {}
}
