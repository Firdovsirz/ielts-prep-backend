package com.ieltsprep.grammar;

import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.Skill;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.grammar.GrammarDtos.DiagnosticAnswerResult;
import com.ieltsprep.grammar.GrammarDtos.DiagnosticQuestionView;
import com.ieltsprep.grammar.GrammarDtos.DiagnosticView;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import com.ieltsprep.session.SessionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs the adaptive 40-question grammar diagnostic and stores per-area proficiency. */
@Service
public class DiagnosticService {

    private final GrammarDiagnosticRepository diagnostics;
    private final ItemRepository items;
    private final GrammarTaxonomy taxonomy;
    private final GrammarService grammar;
    private final SessionService sessions;
    private final AttemptRepository attempts;
    private final Clock clock;

    public DiagnosticService(GrammarDiagnosticRepository diagnostics, ItemRepository items, GrammarTaxonomy taxonomy,
            GrammarService grammar, SessionService sessions, AttemptRepository attempts, Clock clock) {
        this.diagnostics = diagnostics;
        this.items = items;
        this.taxonomy = taxonomy;
        this.grammar = grammar;
        this.sessions = sessions;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional
    public DiagnosticView start() {
        GrammarDiagnostic existing = diagnostics.findFirstByStatusOrderByIdDesc("IN_PROGRESS").orElse(null);
        if (existing != null) {
            return view(existing);
        }
        GrammarDiagnostic d = new GrammarDiagnostic();
        d.setStatus("IN_PROGRESS");
        d.setStartedAt(Instant.now(clock));
        d.setState(Json.write(AdaptiveDiagnostic.start(taxonomy.areas().stream().map(GrammarTaxonomy.Area::key).toList())));
        return view(diagnostics.save(d));
    }

    public DiagnosticView get(long id) {
        return view(diagnostics.findById(id).orElseThrow(() -> ApiException.notFound("Diagnostic " + id)));
    }

    @Transactional
    public DiagnosticAnswerResult answer(long id, GrammarDtos.DiagnosticAnswerRequest req) {
        GrammarDiagnostic d = diagnostics.findById(id).orElseThrow(() -> ApiException.notFound("Diagnostic " + id));
        if (!"IN_PROGRESS".equals(d.getStatus())) {
            throw ApiException.conflict("Diagnostic already finished");
        }
        AdaptiveDiagnostic.State state = Json.read(d.getState(), AdaptiveDiagnostic.State.class);
        Map<String, GrammarContent.DiagnosticQuestion> bank = bank();
        String expectedId = AdaptiveDiagnostic.next(state, bank.keySet()).orElseThrow(() -> ApiException.conflict("No question pending"));
        if (!expectedId.equals(req.questionId())) {
            throw ApiException.badRequest("Answer the current question (" + expectedId + ") first");
        }
        GrammarContent.DiagnosticQuestion q = bank.get(expectedId);
        boolean correct = q.answer().equalsIgnoreCase(req.answer() == null ? "" : req.answer().trim());
        state = AdaptiveDiagnostic.record(state, new AdaptiveDiagnostic.Answer(q.id(), q.area(), q.level(), correct));
        d.setState(Json.write(state));
        if (state.finished() || AdaptiveDiagnostic.next(state, bank.keySet()).isEmpty()) {
            finish(d, state, bank);
        }
        diagnostics.save(d);
        return new DiagnosticAnswerResult(correct, q.answer(), q.explanation(), view(d));
    }

    private void finish(GrammarDiagnostic d, AdaptiveDiagnostic.State state, Map<String, GrammarContent.DiagnosticQuestion> bank) {
        Map<String, Double> scores = AdaptiveDiagnostic.scores(state);
        grammar.recordDiagnostic(scores);
        d.setStatus("COMPLETED");
        d.setFinishedAt(Instant.now(clock));
        PracticeSession s = sessions.start(Skill.GRAMMAR, SessionMode.PRACTICE, SessionKind.GRAMMAR_DIAGNOSTIC, List.of(), null, null);
        int correct = (int) state.answers().stream().filter(AdaptiveDiagnostic.Answer::correct).count();
        Attempt a = new Attempt();
        a.setSessionId(s.getId());
        a.setModule(Skill.GRAMMAR);
        a.setTaskType(TaskType.GRAMMAR_DIAGNOSTIC.name());
        a.setMyAnswers(Json.write(state));
        a.setRawScore(correct);
        a.setMaxScore(state.asked());
        a.setPerQuestion(Json.write(state.answers()));
        a.setFeedback(Json.write(Map.of("scores", scores)));
        a.setStatus(AttemptStatus.MARKED);
        a.setSubmittedAt(Instant.now(clock));
        a.setGradedAt(Instant.now(clock));
        attempts.save(a);
        double mean = scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        sessions.complete(s, null, correct, state.asked(), BigDecimal.valueOf(Math.round(mean) / 10.0));
    }

    private DiagnosticView view(GrammarDiagnostic d) {
        AdaptiveDiagnostic.State state = Json.read(d.getState(), AdaptiveDiagnostic.State.class);
        if ("COMPLETED".equals(d.getStatus())) {
            return new DiagnosticView(d.getId(), d.getStatus(), state.asked(), AdaptiveDiagnostic.LENGTH, null, AdaptiveDiagnostic.scores(state));
        }
        Map<String, GrammarContent.DiagnosticQuestion> bank = bank();
        DiagnosticQuestionView question = AdaptiveDiagnostic.next(state, bank.keySet()).map(bank::get)
                .map(q -> new DiagnosticQuestionView(q.id(), q.area(), taxonomy.require(q.area()).name(), q.level(), q.prompt(), q.options()))
                .orElse(null);
        return new DiagnosticView(d.getId(), d.getStatus(), state.asked(), AdaptiveDiagnostic.LENGTH, question, null);
    }

    private Map<String, GrammarContent.DiagnosticQuestion> bank() {
        return items.findByTaskTypeAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_DIAGNOSTIC, VerificationStatus.VERIFIED).stream()
                .map((Item i) -> i.contentAs(GrammarContent.DiagnosticQuestion.class))
                .collect(Collectors.toMap(GrammarContent.DiagnosticQuestion::id, q -> q, (a, b) -> a, LinkedHashMap::new));
    }
}
