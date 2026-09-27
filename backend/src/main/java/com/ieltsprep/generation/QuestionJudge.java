package com.ieltsprep.generation;

import com.ieltsprep.content.model.QuestionType;
import com.ieltsprep.content.model.Questions.Question;
import com.ieltsprep.content.model.Questions.QuestionGroup;
import com.ieltsprep.marking.AnswerMarker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Compares the verifier's blind answers with the generated key using the real marking rules. */
final class QuestionJudge {

    private QuestionJudge() {}

    static Verdict judge(List<QuestionGroup> groups, VerificationReport report) {
        List<String> reasons = new ArrayList<>();
        Map<String, VerificationReport.BlindAnswer> blind = report.blindAnswers() == null ? Map.of()
                : report.blindAnswers().stream().collect(Collectors.toMap(b -> b.id().trim(), Function.identity(), (a, b) -> a));
        for (QuestionGroup g : groups) {
            if (g.questionType() == QuestionType.MULTIPLE_CHOICE_MULTI) {
                List<String> key = g.questions().stream().map(q -> q.answers().getFirst()).toList();
                List<String> given = g.questions().stream()
                        .map(q -> blind.containsKey(String.valueOf(q.number())) ? blind.get(String.valueOf(q.number())).answer() : null)
                        .toList();
                List<AnswerMarker.Result> marked = AnswerMarker.markUnordered(given, key);
                for (int i = 0; i < marked.size(); i++) {
                    if (!marked.get(i).correct()) {
                        reasons.add("Q" + g.questions().get(i).number() + ": verifier chose " + given.get(i) + " but the key is " + key);
                    }
                }
                continue;
            }
            for (Question q : g.questions()) {
                VerificationReport.BlindAnswer b = blind.get(String.valueOf(q.number()));
                if (b == null) {
                    reasons.add("Q" + q.number() + ": verifier gave no answer");
                    continue;
                }
                AnswerMarker.Result r = g.isChoice()
                        ? AnswerMarker.markChoice(b.answer(), q.answers())
                        : AnswerMarker.markText(b.answer(), q.answers(), 0);
                if (!r.correct()) {
                    reasons.add("Q" + q.number() + " (" + g.questionType() + "): an independent solver answered '" + b.answer()
                            + "' (" + b.evidence() + ") but the key says " + q.answers() + " — the item is ambiguous or the key is wrong");
                } else if ("low".equalsIgnoreCase(b.confidence())) {
                    reasons.add("Q" + q.number() + ": solver reached the key only with low confidence — make the evidence unambiguous");
                }
            }
        }
        report.blockers().forEach(i -> reasons.add("Q" + i.id() + " " + i.kind() + ": " + i.detail()));
        if (!report.passed() && reasons.isEmpty()) {
            reasons.add("Verifier verdict FAIL: " + report.summary());
        }
        String notes = "Blind solve: " + (blind.size()) + " answers; " + report.summary();
        return reasons.isEmpty() ? Verdict.pass(notes) : Verdict.fail(reasons, notes);
    }

    /** Review-only items (prompts, lessons, word banks): pass unless the reviewer found a blocker. */
    static Verdict review(VerificationReport report) {
        List<String> reasons = new ArrayList<>();
        report.blockers().forEach(i -> reasons.add(i.kind() + ": " + i.detail()));
        if (!report.passed() && reasons.isEmpty()) {
            reasons.add("Reviewer verdict FAIL: " + report.summary());
        }
        return reasons.isEmpty() ? Verdict.pass(report.summary()) : Verdict.fail(reasons, report.summary());
    }
}
