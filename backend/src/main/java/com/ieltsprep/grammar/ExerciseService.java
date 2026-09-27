package com.ieltsprep.grammar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemService;
import com.ieltsprep.content.Skill;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.errorlog.ErrorEntryRepository;
import com.ieltsprep.grammar.GrammarDtos.ExerciseSessionView;
import com.ieltsprep.grammar.GrammarDtos.ItemResult;
import com.ieltsprep.grammar.GrammarDtos.ResultView;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import com.ieltsprep.session.SessionService;
import com.ieltsprep.session.SessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grammar exercise sets and error drills: serve without answers, mark closed items exactly, send open or non-matching
 * answers to Claude in one batch (grammar-answer-check), and fall back to self-assessment when no API key is set.
 */
@Service
public class ExerciseService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseService.class);
    static final String OWN = "own";

    private final ItemService items;
    private final SessionService sessions;
    private final AttemptRepository attempts;
    private final GrammarService grammar;
    private final GrammarTaxonomy taxonomy;
    private final ErrorDrillService drills;
    private final ErrorEntryRepository errors;
    private final ClaudeService claude;
    private final Clock clock;

    public ExerciseService(ItemService items, SessionService sessions, AttemptRepository attempts, GrammarService grammar,
            GrammarTaxonomy taxonomy, ErrorDrillService drills, ErrorEntryRepository errors, ClaudeService claude, Clock clock) {
        this.items = items;
        this.sessions = sessions;
        this.attempts = attempts;
        this.grammar = grammar;
        this.taxonomy = taxonomy;
        this.drills = drills;
        this.errors = errors;
        this.claude = claude;
        this.clock = clock;
    }

    /** What the session presents: a normalised list of items plus drill metadata. */
    record Content(String area, String title, String instructions, String exerciseType, List<GrammarContent.ExerciseItem> items,
            String ownSentence, String rule, String subtype) {}

    @Transactional
    public ExerciseSessionView startExercise(long itemId) {
        Item item = items.take(itemId);
        if (item.getTaskType() != TaskType.GRAMMAR_EXERCISE) {
            throw ApiException.badRequest("Item " + itemId + " is not a grammar exercise");
        }
        PracticeSession s = sessions.start(Skill.GRAMMAR, SessionMode.PRACTICE, SessionKind.GRAMMAR_EXERCISE, List.of(itemId), null, null);
        return view(s);
    }

    @Transactional
    public ExerciseSessionView startDrill(String subtype) {
        Item drill = drills.drillFor(subtype);
        items.take(drill.getId());
        PracticeSession s = sessions.start(Skill.GRAMMAR, SessionMode.PRACTICE, SessionKind.GRAMMAR_ERROR_DRILL, List.of(drill.getId()), null, null);
        return view(s);
    }

    public ExerciseSessionView view(long sessionId) {
        return view(sessions.require(sessionId, Skill.GRAMMAR));
    }

    private ExerciseSessionView view(PracticeSession s) {
        Content c = content(s);
        return new ExerciseSessionView(s.getId(), s.getKind().name(), c.area(), taxonomy.require(c.area()).name(), c.title(),
                c.instructions(), c.exerciseType(), c.items().stream().map(GrammarContent.ExerciseItem::withoutAnswers).toList(),
                c.ownSentence(), s.getStatus().name());
    }

    Content content(PracticeSession s) {
        Item item = items.get(s.itemIdList().getFirst());
        if (item.getTaskType() == TaskType.GRAMMAR_ERROR_DRILL) {
            GrammarContent.ErrorDrill d = item.contentAs(GrammarContent.ErrorDrill.class);
            List<GrammarContent.ExerciseItem> list = new ArrayList<>();
            list.add(new GrammarContent.ExerciseItem(OWN, "This is your own sentence. Correct it: " + d.ownSentence(), List.of(),
                    d.ownSentenceCorrections(), d.ownSentenceCorrections().isEmpty() ? "" : d.ownSentenceCorrections().getFirst(),
                    "EXACT_OR_AI", d.ownSentenceExplanation()));
            list.addAll(d.freshItems());
            return new Content(d.area(), "Fix your pattern: " + d.subtype().replace('_', ' '),
                    "First correct the sentence you wrote, then answer three new items that test the same rule.", "ERROR_DRILL", list,
                    d.ownSentence(), d.rule(), d.subtype());
        }
        GrammarContent.ExerciseSet set = item.contentAs(GrammarContent.ExerciseSet.class);
        return new Content(set.area(), set.title(), set.instructions(), set.exerciseType(), set.items(), null, null, null);
    }

    @Transactional
    public ResultView submit(long sessionId, GrammarDtos.SubmitRequest req) {
        PracticeSession s = sessions.require(sessionId, Skill.GRAMMAR);
        sessions.ensureOpen(s);
        Content c = content(s);
        Map<String, String> answers = req.answers() == null ? Map.of() : req.answers();
        List<ItemResult> results = new ArrayList<>();
        List<GrammarContent.ExerciseItem> needAi = new ArrayList<>();
        for (GrammarContent.ExerciseItem i : c.items()) {
            String given = answers.getOrDefault(i.id(), "").trim();
            if (given.isBlank()) {
                results.add(result(i, given, false, "EXACT", "No answer given.", null));
            } else if (GrammarMarker.matches(given, i.acceptedAnswers())) {
                results.add(result(i, given, true, "EXACT", null, null));
            } else if ("EXACT".equals(i.checkMode())) {
                results.add(result(i, given, false, "EXACT", null, null));
            } else if (claude.isAvailable()) {
                needAi.add(i);
                results.add(result(i, given, null, "PENDING", null, null));
            } else {
                results.add(result(i, given, null, "SELF", "Compare with the model answer and mark it yourself.", null));
            }
        }
        if (!needAi.isEmpty()) {
            results = aiCheck(c, answers, needAi, results);
        }
        int correct = (int) results.stream().filter(r -> Boolean.TRUE.equals(r.correct())).count();
        int decided = (int) results.stream().filter(r -> r.correct() != null).count();
        double proficiency = grammar.recordResult(c.area(), correct, decided, s.getKind() == SessionKind.GRAMMAR_ERROR_DRILL);

        Attempt a = new Attempt();
        a.setSessionId(s.getId());
        a.setItemId(s.itemIdList().getFirst());
        a.setModule(Skill.GRAMMAR);
        a.setTaskType(s.getKind() == SessionKind.GRAMMAR_ERROR_DRILL ? TaskType.GRAMMAR_ERROR_DRILL.name() : TaskType.GRAMMAR_EXERCISE.name());
        a.setMyAnswers(Json.write(answers));
        a.setRawScore(correct);
        a.setMaxScore(results.size());
        a.setPerQuestion(Json.write(results));
        a.setFeedback(Json.write(Map.of("area", c.area(), "proficiency", proficiency)));
        a.setStatus(AttemptStatus.MARKED);
        a.setSubmittedAt(Instant.now(clock));
        a.setGradedAt(Instant.now(clock));
        attempts.save(a);
        sessions.complete(s, null, correct, results.size(), null);

        if (c.subtype() != null) {
            Instant now = Instant.now(clock);
            errors.findBySubtypeOrderByCreatedAtDesc(c.subtype()).forEach(e -> {
                e.setDrillCount(e.getDrillCount() + 1);
                e.setLastDrilledAt(now);
            });
        }
        return result(sessionId);
    }

    /** Candidate's own verdicts for SELF-marked items (no API key). */
    @Transactional
    public ResultView selfAssess(long sessionId, GrammarDtos.SelfAssessRequest req) {
        PracticeSession s = sessions.require(sessionId, Skill.GRAMMAR);
        Attempt a = attempts.findBySessionIdOrderByIdAsc(sessionId).stream().findFirst()
                .orElseThrow(() -> ApiException.conflict("Submit first"));
        List<ItemResult> results = Json.read(a.getPerQuestion(), new TypeReference<List<ItemResult>>() {});
        int newlyCorrect = 0;
        int newlyDecided = 0;
        List<ItemResult> updated = new ArrayList<>();
        for (ItemResult r : results) {
            Boolean verdict = req.verdicts() == null ? null : req.verdicts().get(r.id());
            if ("SELF".equals(r.method()) && r.correct() == null && verdict != null) {
                updated.add(new ItemResult(r.id(), r.prompt(), r.given(), verdict, "SELF", r.feedback(), r.modelAnswer(),
                        r.acceptedAnswers(), r.explanation(), r.improvedVersion()));
                newlyDecided++;
                newlyCorrect += verdict ? 1 : 0;
            } else {
                updated.add(r);
            }
        }
        if (newlyDecided > 0) {
            Content c = content(s);
            grammar.recordResult(c.area(), newlyCorrect, newlyDecided, false);
            a.setRawScore((int) updated.stream().filter(r -> Boolean.TRUE.equals(r.correct())).count());
            a.setPerQuestion(Json.write(updated));
            attempts.save(a);
            s.setRawScore(a.getRawScore());
            sessions.save(s);
        }
        return result(sessionId);
    }

    public ResultView result(long sessionId) {
        PracticeSession s = sessions.require(sessionId, Skill.GRAMMAR);
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw ApiException.conflict("Not submitted yet");
        }
        Content c = content(s);
        Attempt a = attempts.findBySessionIdOrderByIdAsc(sessionId).getFirst();
        List<ItemResult> results = Json.read(a.getPerQuestion(), new TypeReference<List<ItemResult>>() {});
        int pending = (int) results.stream().filter(r -> r.correct() == null).count();
        Double proficiency = grammar.areas().stream().filter(x -> x.key().equals(c.area())).findFirst()
                .map(GrammarDtos.AreaSummary::proficiency).orElse(null);
        return new ResultView(sessionId, s.getKind().name(), c.area(), taxonomy.require(c.area()).name(),
                a.getRawScore() == null ? 0 : a.getRawScore(), results.size(), pending, results, c.rule(), proficiency);
    }

    private List<ItemResult> aiCheck(Content c, Map<String, String> answers, List<GrammarContent.ExerciseItem> needAi,
            List<ItemResult> results) {
        String rendered = needAi.stream().map(i -> "Item " + i.id() + " [" + c.exerciseType() + "]\nTask: " + i.prompt()
                + "\nModel answer: " + i.modelAnswer() + "\nOther accepted answers: " + i.acceptedAnswers()
                + "\nCandidate's answer: " + answers.getOrDefault(i.id(), "")).collect(Collectors.joining("\n\n"));
        Map<String, AnswerCheck.Result> checked;
        try {
            AnswerCheck check = claude.call(ClaudeCall.of("grammar-answer-check", Map.of("area", c.area(), "items", rendered),
                    AnswerCheck.class).ref("grammar check " + c.area()));
            checked = check.results().stream().collect(Collectors.toMap(AnswerCheck.Result::id, r -> r, (x, y) -> x));
        } catch (Exception e) {
            log.warn("Grammar answer check failed, falling back to self-assessment: {}", e.getMessage());
            checked = Map.of();
        }
        List<ItemResult> out = new ArrayList<>();
        for (ItemResult r : results) {
            if (!"PENDING".equals(r.method())) {
                out.add(r);
                continue;
            }
            AnswerCheck.Result ai = checked.get(r.id());
            out.add(ai == null
                    ? new ItemResult(r.id(), r.prompt(), r.given(), null, "SELF", "Automatic check unavailable — mark it yourself.",
                            r.modelAnswer(), r.acceptedAnswers(), r.explanation(), null)
                    : new ItemResult(r.id(), r.prompt(), r.given(), ai.correct(), "AI", ai.feedback(), r.modelAnswer(),
                            r.acceptedAnswers(), r.explanation(), ai.improvedVersion()));
        }
        return out;
    }

    private static ItemResult result(GrammarContent.ExerciseItem i, String given, Boolean correct, String method, String feedback,
            String improved) {
        return new ItemResult(i.id(), i.prompt(), given, correct, method, feedback, i.modelAnswer(), i.acceptedAnswers(),
                i.explanation(), improved);
    }
}
