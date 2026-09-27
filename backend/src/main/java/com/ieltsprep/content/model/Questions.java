package com.ieltsprep.content.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Question-group model shared by Reading and Listening; mirrors the {@code group} definition in
 * {@code prompts/schemas/reading-passage.schema.json} and {@code listening-section.schema.json}.
 */
public final class Questions {

    private Questions() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionOption(String key, String text) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Question(
            int number,
            String prompt,
            List<QuestionOption> options,
            List<String> answers,
            String justificationSpan,
            String location) {

        public Question withoutAnswers() {
            return new Question(number, prompt, options, List.of(), "", "");
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TableSpec(String title, List<String> columns, List<List<String>> rows) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DiagramNode(String id, String label, double x, double y) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DiagramEdge(String from, String to, String label) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DiagramSpec(String title, List<DiagramNode> nodes, List<DiagramEdge> edges) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MapFeature(String label, String kind, double x, double y, double w, double h) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MapMarker(String key, double x, double y) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MapSpec(String title, List<MapFeature> features, List<MapMarker> markers) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionGroup(
            String groupId,
            QuestionType questionType,
            String instructions,
            int wordLimit,
            boolean numberAllowed,
            List<QuestionOption> options,
            String context,
            TableSpec table,
            List<String> flowSteps,
            DiagramSpec diagram,
            MapSpec map,
            List<Question> questions) {

        public QuestionGroup withoutAnswers() {
            return new QuestionGroup(groupId, questionType, instructions, wordLimit, numberAllowed, options, context,
                    table, flowSteps, diagram, map, questions.stream().map(Question::withoutAnswers).toList());
        }

        /** Choice-marked when the type is a choice type or a word bank is supplied. */
        public boolean isChoice() {
            return questionType.isChoice() || (options != null && !options.isEmpty());
        }
    }

    /** Flattened answer-key entry, stored in {@code items.answer_key}. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnswerKeyEntry(
            int number,
            String groupId,
            QuestionType questionType,
            List<String> answers,
            String justificationSpan,
            String location,
            int wordLimit,
            boolean choice) {}

    public static List<AnswerKeyEntry> answerKey(List<QuestionGroup> groups) {
        return groups.stream()
                .flatMap(g -> g.questions().stream().map(q -> new AnswerKeyEntry(q.number(), g.groupId(),
                        g.questionType(), q.answers(), q.justificationSpan(), q.location(), g.wordLimit(), g.isChoice())))
                .toList();
    }

    public static int questionCount(List<QuestionGroup> groups) {
        return groups.stream().mapToInt(g -> g.questions().size()).sum();
    }
}
