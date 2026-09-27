package com.ieltsprep.content;

import com.ieltsprep.content.model.QuestionType;
import com.ieltsprep.content.model.Questions.Question;
import com.ieltsprep.content.model.Questions.QuestionGroup;
import com.ieltsprep.content.model.Questions.QuestionOption;
import com.ieltsprep.grammar.GrammarContent;
import com.ieltsprep.listening.ListeningSection;
import com.ieltsprep.marking.AnswerMarker;
import com.ieltsprep.reading.ReadingPassage;
import com.ieltsprep.speaking.SpeakingPrompts;
import com.ieltsprep.writing.WritingPrompts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Deterministic structural checks run on every generated item before the independent Claude verification pass
 * (and on seed files in tests). Mirrors scripts/validate-seed.mjs.
 */
@Component
public class ItemValidator {

    public record Report(List<String> errors, List<String> warnings) {
        public boolean ok() {
            return errors.isEmpty();
        }

        public String summary() {
            return String.join("; ", errors);
        }
    }

    public Report validate(TaskType type, Object content) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        switch (type) {
            case READING_PASSAGE -> reading((ReadingPassage) content, errors, warnings);
            case LISTENING_SECTION -> listening((ListeningSection) content, errors, warnings);
            case WRITING_TASK1_ACADEMIC -> task1((WritingPrompts.Task1Academic) content, errors, warnings);
            case WRITING_TASK1_GENERAL -> {
                if (((WritingPrompts.Task1General) content).bulletPoints().size() != 3) {
                    errors.add("letter needs exactly 3 bullet points");
                }
            }
            case SPEAKING_PART1 -> {
                if (((SpeakingPrompts.Part1) content).topics().size() != 3) {
                    errors.add("Part 1 needs 3 topics");
                }
            }
            case SPEAKING_PART2 -> {
                SpeakingPrompts.CueCard card = ((SpeakingPrompts.Part2) content).cueCard();
                if (card.bullets().size() != 3) {
                    errors.add("cue card needs 3 bullets");
                }
                if (!card.explain().toLowerCase(Locale.ROOT).startsWith("and explain")) {
                    errors.add("cue card must end with 'and explain …'");
                }
            }
            case SPEAKING_PART3 -> {
                if (((SpeakingPrompts.Part3) content).questions().size() < 4) {
                    errors.add("Part 3 needs at least 4 questions");
                }
            }
            case GRAMMAR_EXERCISE -> {
                GrammarContent.ExerciseSet set = (GrammarContent.ExerciseSet) content;
                if (set.items().size() < 3) {
                    errors.add("exercise needs at least 3 items");
                }
                set.items().stream().filter(i -> "EXACT".equals(i.checkMode()) && i.acceptedAnswers().isEmpty())
                        .forEach(i -> errors.add("item " + i.id() + ": EXACT item without accepted answers"));
            }
            case GRAMMAR_ERROR_DRILL -> {
                GrammarContent.ErrorDrill drill = (GrammarContent.ErrorDrill) content;
                if (drill.freshItems().size() != 3) {
                    errors.add("error drill needs exactly 3 fresh items");
                }
                if (drill.ownSentenceCorrections().isEmpty()) {
                    errors.add("error drill needs a correction of the candidate's sentence");
                }
            }
            default -> {
                // lessons, diagnostic questions and word banks: schema-level checks are enough
            }
        }
        return new Report(errors, warnings);
    }

    void reading(ReadingPassage p, List<String> errors, List<String> warnings) {
        int words = p.wordCount();
        if (words < 650 || words > 1000) {
            errors.add("passage has " + words + " words (expected 700–900)");
        }
        Set<String> labels = p.paragraphs().stream().map(ReadingPassage.Paragraph::label).collect(Collectors.toSet());
        if (labels.size() != p.paragraphs().size()) {
            errors.add("duplicate paragraph labels");
        }
        int expected = p.difficulty() == 3 ? 14 : 13;
        groups(p.questionGroups(), p.fullText(), expected, false, List.of(), errors, warnings);
        if (p.questionGroups().stream().map(QuestionGroup::questionType).distinct().count() < 2) {
            errors.add("passage needs at least two question types");
        }
        for (QuestionGroup g : p.questionGroups()) {
            for (Question q : g.questions()) {
                if (!labels.contains(q.location())) {
                    errors.add("Q" + q.number() + ": location '" + q.location() + "' is not a paragraph label");
                }
            }
        }
    }

    void listening(ListeningSection s, List<String> errors, List<String> warnings) {
        Set<String> speakers = s.speakers().stream().map(ListeningSection.Speaker::id).collect(Collectors.toSet());
        for (int i = 0; i < s.script().size(); i++) {
            if (!speakers.contains(s.script().get(i).speaker())) {
                errors.add("script line " + i + " has unknown speaker " + s.script().get(i).speaker());
            }
        }
        if (s.section() == 4 && s.speakers().size() != 1) {
            errors.add("section 4 must be a monologue");
        }
        if ((s.section() == 1 || s.section() == 3) && s.speakers().size() < 2) {
            errors.add("section " + s.section() + " needs two or more speakers");
        }
        int nextQuestion = 1;
        int lastLine = -1;
        for (ListeningSection.ListeningPart part : s.parts()) {
            if (part.questionsFrom() != nextQuestion) {
                errors.add("parts must cover the questions contiguously");
            }
            if (part.startLine() <= lastLine || part.startLine() >= s.script().size()) {
                errors.add("part start_line " + part.startLine() + " is invalid");
            }
            nextQuestion = part.questionsTo() + 1;
            lastLine = part.startLine();
        }
        if (nextQuestion != 11) {
            errors.add("parts must end at question 10");
        }
        if (!s.parts().isEmpty() && s.parts().getFirst().startLine() != 0) {
            errors.add("first part must start at line 0");
        }
        List<String> lines = s.script().stream().map(ListeningSection.ScriptLine::text).toList();
        groups(s.questionGroups(), String.join("\n", lines), 10, true, lines, errors, warnings);
    }

    void task1(WritingPrompts.Task1Academic t, List<String> errors, List<String> warnings) {
        switch (t.chartType()) {
            case "LINE", "BAR", "PIE", "TABLE" -> {
                if (t.categories().isEmpty() || t.series().isEmpty()) {
                    errors.add("chart needs categories and series");
                }
                for (WritingPrompts.ChartSeries series : t.series()) {
                    if (series.values().size() != t.categories().size()) {
                        errors.add("series " + series.name() + " has " + series.values().size() + " values for "
                                + t.categories().size() + " categories");
                    }
                    if ("PIE".equals(t.chartType())) {
                        double sum = series.values().stream().mapToDouble(Double::doubleValue).sum();
                        if (Math.abs(sum - 100) > 1.5) {
                            warnings.add("pie " + series.name() + " sums to " + sum);
                        }
                    }
                }
            }
            case "PROCESS" -> {
                if (t.processSteps().size() < 5) {
                    errors.add("process needs at least 5 steps");
                }
            }
            case "MAP" -> {
                if (t.maps().size() != 2) {
                    errors.add("map comparison needs exactly two maps");
                }
            }
            default -> errors.add("unknown chart type " + t.chartType());
        }
    }

    private void groups(List<QuestionGroup> groups, String source, int expectedCount, boolean listening,
            List<String> scriptLines, List<String> errors, List<String> warnings) {
        List<Integer> numbers = groups.stream().flatMap(g -> g.questions().stream()).map(Question::number).toList();
        List<Integer> contiguous = IntStream.rangeClosed(1, numbers.size()).boxed().toList();
        if (!numbers.equals(contiguous)) {
            errors.add("question numbers must run 1.." + numbers.size() + " in order, got " + numbers);
        }
        if (numbers.size() != expectedCount) {
            errors.add("expected " + expectedCount + " questions, got " + numbers.size());
        }
        String src = norm(source);
        for (QuestionGroup g : groups) {
            String tag = g.groupId() + " (" + g.questionType() + ")";
            Set<String> groupKeys = g.options().stream().map(QuestionOption::key).collect(Collectors.toSet());
            if (g.questionType() == QuestionType.MATCHING_HEADINGS && g.options().size() <= g.questions().size()) {
                errors.add(tag + ": needs more headings than paragraphs");
            }
            if (g.questionType() == QuestionType.MULTIPLE_CHOICE_MULTI) {
                List<String> letters = g.questions().stream().map(q -> q.answers().isEmpty() ? "" : q.answers().getFirst()).toList();
                if (new HashSet<>(letters).size() != letters.size()) {
                    errors.add(tag + ": answers must be distinct letters");
                }
            }
            String gapText = String.join(" ", g.context(), String.join(" ", g.table().rows().stream().flatMap(List::stream).toList()),
                    String.join(" ", g.flowSteps()),
                    g.diagram().nodes().stream().map(n -> n.label()).collect(Collectors.joining(" ")));
            boolean contextual = switch (g.questionType()) {
                case SUMMARY_COMPLETION, NOTE_COMPLETION, FORM_COMPLETION, TABLE_COMPLETION, FLOW_CHART_COMPLETION,
                        DIAGRAM_LABEL_COMPLETION -> true;
                default -> false;
            };
            int lastPos = -1;
            for (Question q : g.questions()) {
                String qt = tag + " Q" + q.number();
                if (contextual && !gapText.contains("{{" + q.number() + "}}")) {
                    errors.add(qt + ": gap {{" + q.number() + "}} missing");
                }
                if (q.answers().isEmpty()) {
                    errors.add(qt + ": no answer");
                    continue;
                }
                if (g.isChoice()) {
                    Set<String> keys = switch (g.questionType()) {
                        case TRUE_FALSE_NOT_GIVEN -> Set.of("TRUE", "FALSE", "NOT GIVEN");
                        case YES_NO_NOT_GIVEN -> Set.of("YES", "NO", "NOT GIVEN");
                        default -> {
                            Set<String> all = new HashSet<>(groupKeys);
                            q.options().forEach(o -> all.add(o.key()));
                            yield all;
                        }
                    };
                    q.answers().stream().filter(a -> !keys.contains(a))
                            .forEach(a -> errors.add(qt + ": answer '" + a + "' is not an option"));
                } else {
                    if (g.wordLimit() > 0) {
                        q.answers().stream().filter(a -> AnswerMarker.countWords(a.replaceAll("[()]", "")) > g.wordLimit())
                                .forEach(a -> errors.add(qt + ": answer '" + a + "' exceeds the word limit"));
                    }
                    if (!listening && !src.contains(norm(q.answers().getFirst().replaceAll("[()]", "")))) {
                        errors.add(qt + ": answer '" + q.answers().getFirst() + "' is not in the passage");
                    }
                    if (q.prompt() != null && q.answers().stream().anyMatch(a -> a.length() > 3 && norm(q.prompt()).contains(norm(a)))) {
                        errors.add(qt + ": answer leaked in the stem");
                    }
                }
                String span = norm(q.justificationSpan());
                int pos = span.isEmpty() ? -1 : src.indexOf(span);
                if (span.isEmpty()) {
                    errors.add(qt + ": empty justification_span");
                } else if (pos < 0) {
                    errors.add(qt + ": justification_span is not verbatim in the text");
                }
                if (listening && pos >= 0) {
                    try {
                        int line = Integer.parseInt(q.location().trim());
                        if (line < 0 || line >= scriptLines.size() || !norm(scriptLines.get(line)).contains(span)) {
                            warnings.add(qt + ": location is not the line containing the span");
                        }
                    } catch (NumberFormatException e) {
                        warnings.add(qt + ": location is not a line index");
                    }
                }
                if (g.questionType().followsTextOrder() && pos >= 0) {
                    if (pos < lastPos) {
                        warnings.add(qt + ": not in text order");
                    }
                    lastPos = pos;
                }
            }
        }
    }

    static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\u2018\\u2019]", "'")
                .replaceAll("[\\u201C\\u201D]", "\"")
                .replaceAll("[\\u2013\\u2014]", "-")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
