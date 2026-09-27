package com.ieltsprep.marking;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ieltsprep.content.model.QuestionType;
import com.ieltsprep.content.model.Questions.Question;
import com.ieltsprep.content.model.Questions.QuestionGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Marks every question of a passage/section against its key. Answers are keyed by local question number. */
public final class QuestionMarker {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionResult(
            int number,
            String groupId,
            QuestionType questionType,
            String given,
            List<String> expected,
            boolean correct,
            boolean blank,
            String justification,
            String location,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String note) {}

    private QuestionMarker() {}

    public static List<QuestionResult> mark(List<QuestionGroup> groups, Map<Integer, String> answers) {
        List<QuestionResult> results = new ArrayList<>();
        for (QuestionGroup g : groups) {
            if (g.questionType() == QuestionType.MULTIPLE_CHOICE_MULTI) {
                List<String> key = g.questions().stream().map(q -> q.answers().getFirst()).toList();
                List<String> given = g.questions().stream().map(q -> answers.get(q.number())).toList();
                List<AnswerMarker.Result> marked = AnswerMarker.markUnordered(given, key);
                for (int i = 0; i < g.questions().size(); i++) {
                    Question q = g.questions().get(i);
                    results.add(result(g, q, given.get(i), key, marked.get(i)));
                }
                continue;
            }
            for (Question q : g.questions()) {
                String given = answers.get(q.number());
                AnswerMarker.Result r = g.isChoice()
                        ? AnswerMarker.markChoice(given, q.answers())
                        : AnswerMarker.markText(given, q.answers(), g.wordLimit());
                results.add(result(g, q, given, q.answers(), r));
            }
        }
        return results;
    }

    public static int score(List<QuestionResult> results) {
        return (int) results.stream().filter(QuestionResult::correct).count();
    }

    private static QuestionResult result(QuestionGroup g, Question q, String given, List<String> expected, AnswerMarker.Result r) {
        return new QuestionResult(q.number(), g.groupId(), g.questionType(), given == null ? "" : given.trim(), expected,
                r.correct(), r.blank(), q.justificationSpan(), q.location(), r.reason());
    }
}
