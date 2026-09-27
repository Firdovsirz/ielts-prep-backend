package com.ieltsprep.generation;

import static com.ieltsprep.generation.QuestionPlan.seg;

import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.listening.ListeningSection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ListeningBlueprint implements Blueprint {

    static final Map<Integer, List<QuestionPlan>> PLANS = Map.of(
            1, List.of(
                    QuestionPlan.of(seg(1, 5, "FORM_COMPLETION", "ONE WORD AND/OR A NUMBER"), seg(6, 10, "FORM_COMPLETION", "ONE WORD AND/OR A NUMBER")),
                    QuestionPlan.of(seg(1, 6, "NOTE_COMPLETION", "ONE WORD AND/OR A NUMBER"), seg(7, 10, "MULTIPLE_CHOICE", "A–C")),
                    QuestionPlan.of(seg(1, 5, "TABLE_COMPLETION", "NO MORE THAN TWO WORDS AND/OR A NUMBER"), seg(6, 10, "NOTE_COMPLETION", "ONE WORD AND/OR A NUMBER")),
                    QuestionPlan.of(seg(1, 4, "FORM_COMPLETION", "ONE WORD AND/OR A NUMBER"), seg(5, 10, "TABLE_COMPLETION", "ONE WORD AND/OR A NUMBER"))),
            2, List.of(
                    QuestionPlan.of(seg(1, 4, "MULTIPLE_CHOICE", "A–C"), seg(5, 10, "MAP_LABELLING", "letters A–H on a plan/map")),
                    QuestionPlan.of(seg(1, 5, "MATCHING", "box of options A–G"), seg(6, 10, "NOTE_COMPLETION", "ONE WORD AND/OR A NUMBER")),
                    QuestionPlan.of(seg(1, 2, "MULTIPLE_CHOICE_MULTI", "Choose TWO letters, A–E"), seg(3, 4, "MULTIPLE_CHOICE_MULTI", "Choose TWO letters, A–E"),
                            seg(5, 10, "MAP_LABELLING", "letters A–H on a plan/map")),
                    QuestionPlan.of(seg(1, 6, "MULTIPLE_CHOICE", "A–C"), seg(7, 10, "SENTENCE_COMPLETION", "NO MORE THAN TWO WORDS"))),
            3, List.of(
                    QuestionPlan.of(seg(1, 5, "MULTIPLE_CHOICE", "A–C"), seg(6, 10, "MATCHING", "box of comments A–G")),
                    QuestionPlan.of(seg(1, 2, "MULTIPLE_CHOICE_MULTI", "Choose TWO letters, A–E"), seg(3, 4, "MULTIPLE_CHOICE_MULTI", "Choose TWO letters, A–E"),
                            seg(5, 10, "FLOW_CHART_COMPLETION", "ONE WORD ONLY")),
                    QuestionPlan.of(seg(1, 3, "SHORT_ANSWER", "NO MORE THAN TWO WORDS"), seg(4, 6, "SENTENCE_COMPLETION", "NO MORE THAN TWO WORDS"),
                            seg(7, 10, "MATCHING", "options A–F")),
                    QuestionPlan.of(seg(1, 4, "MULTIPLE_CHOICE", "A–C"), seg(5, 10, "TABLE_COMPLETION", "ONE WORD ONLY"))),
            4, List.of(
                    QuestionPlan.of(seg(1, 10, "NOTE_COMPLETION", "ONE WORD ONLY")),
                    QuestionPlan.of(seg(1, 3, "MULTIPLE_CHOICE", "A–C"), seg(4, 10, "NOTE_COMPLETION", "ONE WORD ONLY")),
                    QuestionPlan.of(seg(1, 5, "TABLE_COMPLETION", "ONE WORD ONLY"), seg(6, 10, "SUMMARY_COMPLETION", "ONE WORD ONLY")),
                    QuestionPlan.of(seg(1, 6, "NOTE_COMPLETION", "ONE WORD ONLY"), seg(7, 10, "FLOW_CHART_COMPLETION", "ONE WORD ONLY"))));

    static final Map<Integer, List<String>> SCENARIOS = Map.of(
            1, List.of("booking a room for a community event", "enquiring about joining a sports or hobby club",
                    "arranging a car or equipment hire", "reporting a problem to a letting agent", "registering at a medical centre",
                    "booking a place on an evening course", "arranging a delivery or removal", "applying for a part-time job by phone",
                    "booking a tour or excursion", "asking about a volunteering opportunity"),
            2, List.of("a guide introducing a heritage site", "an induction talk for new volunteers at a charity",
                    "a radio feature about a new public facility", "a talk about changes to a local transport service",
                    "a welcome talk at a university open day", "a museum audio-guide introduction", "a presentation about a local festival",
                    "a manager briefing staff on a new building layout"),
            3, List.of("two students and a tutor planning a group project", "students discussing feedback on an assignment",
                    "a student and supervisor discussing a research method", "students preparing a seminar presentation",
                    "students comparing notes after a field trip", "a tutorial on writing a dissertation literature review"),
            4, List.of());

    private final ItemRepository items;
    private final SourceSeedService seeds;

    public ListeningBlueprint(ItemRepository items, SourceSeedService seeds) {
        this.items = items;
        this.seeds = seeds;
    }

    @Override
    public TaskType type() {
        return TaskType.LISTENING_SECTION;
    }

    @Override
    public List<String> bufferVariants() {
        return List.of("S1", "S2", "S3", "S4");
    }

    @Override
    public GenerationPlan plan(GenerationRequest request) {
        int section = request.variant() == null ? 1 + ThreadLocalRandom.current().nextInt(4) : Integer.parseInt(request.variant().substring(1));
        List<QuestionPlan> plans = PLANS.get(section);
        long existing = items.countByTaskTypeAndVerificationStatus(TaskType.LISTENING_SECTION, VerificationStatus.VERIFIED);
        QuestionPlan qp = plans.get((int) (existing % plans.size()));
        Map<String, Object> vars = new HashMap<>();
        vars.put("section", section);
        vars.put("question_plan", qp.describe());
        vars.put("avoid_titles", String.join("; ", items.recentTitles(TaskType.LISTENING_SECTION, PageRequest.of(0, 30))));
        String url = null;
        String licence = null;
        if (section == 4 || section == 3) {
            var seed = seeds.pick("listening", null);
            if (seed.isPresent()) {
                vars.put("scenario", section == 4
                        ? "a university lecture on: " + seed.get().title() + " — " + seed.get().summary()
                        : SCENARIOS.get(3).get(ThreadLocalRandom.current().nextInt(SCENARIOS.get(3).size()))
                                + ", about a project on: " + seed.get().title());
                vars.put("background_facts", seeds.extract(seed.get(), 3000));
                url = seed.get().url();
                licence = seed.get().licence() + " (topic seed only; script is original)";
            }
        }
        vars.putIfAbsent("scenario", SCENARIOS.get(section).isEmpty() ? "an academic lecture of your choice"
                : SCENARIOS.get(section).get(ThreadLocalRandom.current().nextInt(SCENARIOS.get(section).size())));
        vars.putIfAbsent("background_facts", "");
        List<String> accents = List.of("british", "australian", "new_zealand", "canadian", "irish", "scottish", "american");
        vars.put("accent_hint", accents.get(ThreadLocalRandom.current().nextInt(accents.size())));
        return new GenerationPlan("listening-generate", vars, url, licence, "Section " + section + ": " + vars.get("scenario"));
    }

    @Override
    public String verifyPrompt() {
        return "listening-verify";
    }

    @Override
    public Map<String, Object> verifyVars(Object content) {
        ListeningSection s = (ListeningSection) content;
        return Map.of("exam_paper", ExamPaperRenderer.listening(s.withoutAnswers(true)));
    }

    @Override
    public Verdict judge(Object content, VerificationReport report) {
        return QuestionJudge.judge(((ListeningSection) content).questionGroups(), report);
    }
}
