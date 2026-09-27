package com.ieltsprep.generation;

import static com.ieltsprep.generation.QuestionPlan.seg;

import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.reading.ReadingPassage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ReadingBlueprint implements Blueprint {

    static final Map<Integer, List<QuestionPlan>> PLANS = Map.of(
            1, List.of(
                    QuestionPlan.of(seg(1, 6, "TRUE_FALSE_NOT_GIVEN", ""), seg(7, 10, "NOTE_COMPLETION", "Choose ONE WORD ONLY from the passage"),
                            seg(11, 13, "SHORT_ANSWER", "Choose NO MORE THAN THREE WORDS from the passage")),
                    QuestionPlan.of(seg(1, 4, "MATCHING_INFORMATION", "which paragraph contains…; any letter may be used more than once"),
                            seg(5, 9, "TABLE_COMPLETION", "NO MORE THAN TWO WORDS AND/OR A NUMBER"), seg(10, 13, "MULTIPLE_CHOICE", "A–D")),
                    QuestionPlan.of(seg(1, 5, "FLOW_CHART_COMPLETION", "ONE WORD ONLY"),
                            seg(6, 9, "DIAGRAM_LABEL_COMPLETION", "NO MORE THAN TWO WORDS"), seg(10, 13, "TRUE_FALSE_NOT_GIVEN", "")),
                    QuestionPlan.of(seg(1, 5, "SENTENCE_COMPLETION", "NO MORE THAN TWO WORDS"), seg(6, 10, "TRUE_FALSE_NOT_GIVEN", ""),
                            seg(11, 13, "SHORT_ANSWER", "NO MORE THAN THREE WORDS AND/OR A NUMBER")),
                    QuestionPlan.of(seg(1, 4, "MULTIPLE_CHOICE", "A–D"), seg(5, 9, "NOTE_COMPLETION", "ONE WORD AND/OR A NUMBER"),
                            seg(10, 13, "TRUE_FALSE_NOT_GIVEN", ""))),
            2, List.of(
                    QuestionPlan.of(seg(1, 7, "MATCHING_HEADINGS", "list of headings i–x, more headings than paragraphs"),
                            seg(8, 11, "SENTENCE_COMPLETION", "NO MORE THAN TWO WORDS"), seg(12, 13, "MULTIPLE_CHOICE_MULTI", "Choose TWO letters, A–E")),
                    QuestionPlan.of(seg(1, 5, "MATCHING_FEATURES", "people/organisations A–E; letters may be used more than once"),
                            seg(6, 10, "SUMMARY_COMPLETION", "word bank A–H (answers are letters)"), seg(11, 13, "TRUE_FALSE_NOT_GIVEN", "")),
                    QuestionPlan.of(seg(1, 5, "YES_NO_NOT_GIVEN", "claims of the writer"),
                            seg(6, 9, "SUMMARY_COMPLETION", "ONE WORD ONLY from the passage"), seg(10, 13, "MULTIPLE_CHOICE", "A–D")),
                    QuestionPlan.of(seg(1, 5, "MATCHING_INFORMATION", "which paragraph contains…"),
                            seg(6, 9, "NOTE_COMPLETION", "NO MORE THAN TWO WORDS"), seg(10, 13, "TRUE_FALSE_NOT_GIVEN", "")),
                    QuestionPlan.of(seg(1, 6, "MATCHING_HEADINGS", "list of headings i–ix"), seg(7, 10, "TRUE_FALSE_NOT_GIVEN", ""),
                            seg(11, 13, "SHORT_ANSWER", "NO MORE THAN THREE WORDS"))),
            3, List.of(
                    QuestionPlan.of(seg(1, 5, "MATCHING_SENTENCE_ENDINGS", "endings A–G"), seg(6, 10, "YES_NO_NOT_GIVEN", "claims of the writer"),
                            seg(11, 14, "MULTIPLE_CHOICE", "A–D, including one on the writer's overall purpose")),
                    QuestionPlan.of(seg(1, 6, "MATCHING_HEADINGS", "list of headings i–ix"),
                            seg(7, 10, "MATCHING_FEATURES", "scholars A–D; letters may be used more than once"),
                            seg(11, 14, "SUMMARY_COMPLETION", "word bank A–H (answers are letters)")),
                    QuestionPlan.of(seg(1, 4, "MATCHING_INFORMATION", "which paragraph contains…"), seg(5, 9, "YES_NO_NOT_GIVEN", "views of the writer"),
                            seg(10, 14, "SENTENCE_COMPLETION", "NO MORE THAN TWO WORDS")),
                    QuestionPlan.of(seg(1, 4, "MULTIPLE_CHOICE", "A–D"), seg(5, 9, "YES_NO_NOT_GIVEN", "claims of the writer"),
                            seg(10, 14, "MATCHING_SENTENCE_ENDINGS", "endings A–H")),
                    QuestionPlan.of(seg(1, 5, "MATCHING_FEATURES", "researchers A–E"),
                            seg(6, 10, "SUMMARY_COMPLETION", "ONE WORD ONLY from the passage"), seg(11, 14, "MULTIPLE_CHOICE", "A–D"))));

    private static final Map<Integer, String> CEFR = Map.of(1, "B2", 2, "B2–C1", 3, "C1–C2");
    private static final Map<Integer, String> CHARACTER = Map.of(
            1, "mainly descriptive and factual, clear structure, like an informative magazine article",
            2, "explanatory, with several named sources or findings that the reader must keep apart",
            3, "argumentative and discursive: competing theories, the writer's own stance, dense academic vocabulary");

    private final ItemRepository items;
    private final SourceSeedService seeds;

    public ReadingBlueprint(ItemRepository items, SourceSeedService seeds) {
        this.items = items;
        this.seeds = seeds;
    }

    @Override
    public TaskType type() {
        return TaskType.READING_PASSAGE;
    }

    @Override
    public List<String> bufferVariants() {
        return List.of("P1", "P2", "P3");
    }

    @Override
    public GenerationPlan plan(GenerationRequest request) {
        int difficulty = request.variant() == null ? 1 + (int) (Math.random() * 3) : Integer.parseInt(request.variant().substring(1));
        List<QuestionPlan> plans = PLANS.get(difficulty);
        long existing = items.countByTaskTypeAndVerificationStatus(TaskType.READING_PASSAGE, com.ieltsprep.content.VerificationStatus.VERIFIED);
        QuestionPlan qp = plans.get((int) (existing % plans.size()));
        Map<String, Object> vars = new HashMap<>();
        vars.put("passage_number", difficulty);
        vars.put("difficulty", difficulty);
        vars.put("question_count", qp.count());
        vars.put("question_plan", qp.describe());
        vars.put("cefr_target", CEFR.get(difficulty));
        vars.put("passage_character", CHARACTER.get(difficulty));
        vars.put("avoid_titles", String.join("; ", items.recentTitles(TaskType.READING_PASSAGE, PageRequest.of(0, 30))));
        var seed = seeds.pick("reading", null);
        String url = null;
        String licence = null;
        if (seed.isPresent()) {
            vars.put("topic_title", seed.get().title());
            vars.put("topic_summary", seed.get().summary());
            vars.put("background_facts", seeds.extract(seed.get(), 4000));
            url = seed.get().url();
            licence = seed.get().licence() + " (topic seed only; passage text is original)";
        } else {
            vars.put("topic_title", "a topic of your choice suitable for IELTS Academic");
            vars.put("topic_summary", "");
            vars.put("background_facts", "");
        }
        return new GenerationPlan("reading-generate", vars, url, licence, "Passage " + difficulty + ": " + vars.get("topic_title"));
    }

    @Override
    public String verifyPrompt() {
        return "reading-verify";
    }

    @Override
    public Map<String, Object> verifyVars(Object content) {
        ReadingPassage p = (ReadingPassage) content;
        return Map.of("exam_paper", ExamPaperRenderer.reading(p.withoutAnswers()),
                "question_count", com.ieltsprep.content.model.Questions.questionCount(p.questionGroups()));
    }

    @Override
    public Verdict judge(Object content, VerificationReport report) {
        return QuestionJudge.judge(((ReadingPassage) content).questionGroups(), report);
    }
}
