package com.ieltsprep.grammar;

import com.ieltsprep.content.TaskType;
import com.ieltsprep.generation.GenerationRequest;
import com.ieltsprep.generation.GenerationService;
import com.ieltsprep.grading.GradingDispatcher;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.claude.ClaudeException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grammar")
@Tag(name = "Grammar")
public class GrammarController {

    public record GenerationQueued(String message) {}

    private final GrammarService grammar;
    private final DiagnosticService diagnostic;
    private final ExerciseService exercises;
    private final GenerationService generation;
    private final GradingDispatcher dispatcher;
    private final ClaudeService claude;

    public GrammarController(GrammarService grammar, DiagnosticService diagnostic, ExerciseService exercises,
            GenerationService generation, GradingDispatcher dispatcher, ClaudeService claude) {
        this.grammar = grammar;
        this.diagnostic = diagnostic;
        this.exercises = exercises;
        this.generation = generation;
        this.dispatcher = dispatcher;
        this.claude = claude;
    }

    @GetMapping
    public GrammarDtos.Overview overview() {
        return grammar.overview();
    }

    @GetMapping("/areas/{area}")
    public GrammarDtos.AreaDetail area(@PathVariable String area) {
        return grammar.area(area);
    }

    /** Queues a freshly generated (and blind-verified) exercise set for this area. */
    @PostMapping("/areas/{area}/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerationQueued generate(@PathVariable String area) {
        if (!claude.isAvailable()) {
            throw ClaudeException.notConfigured();
        }
        dispatcher.dispatch("generate grammar " + area,
                () -> generation.generate(GenerationRequest.of(TaskType.GRAMMAR_EXERCISE, area, false)));
        return new GenerationQueued("A new exercise set is being written and checked; it will appear in about a minute.");
    }

    @PostMapping("/diagnostic")
    public GrammarDtos.DiagnosticView startDiagnostic() {
        return diagnostic.start();
    }

    @GetMapping("/diagnostic/{id}")
    public GrammarDtos.DiagnosticView getDiagnostic(@PathVariable long id) {
        return diagnostic.get(id);
    }

    @PostMapping("/diagnostic/{id}/answer")
    public GrammarDtos.DiagnosticAnswerResult answer(@PathVariable long id, @RequestBody GrammarDtos.DiagnosticAnswerRequest req) {
        return diagnostic.answer(id, req);
    }

    @PostMapping("/exercises/{itemId}/start")
    public GrammarDtos.ExerciseSessionView startExercise(@PathVariable long itemId) {
        return exercises.startExercise(itemId);
    }

    @PostMapping("/error-practice/{subtype}/start")
    public GrammarDtos.ExerciseSessionView startDrill(@PathVariable String subtype) {
        return exercises.startDrill(subtype);
    }

    @GetMapping("/sessions/{id}")
    public GrammarDtos.ExerciseSessionView session(@PathVariable long id) {
        return exercises.view(id);
    }

    @PostMapping("/sessions/{id}/submit")
    public GrammarDtos.ResultView submit(@PathVariable long id, @RequestBody GrammarDtos.SubmitRequest req) {
        return exercises.submit(id, req);
    }

    @PostMapping("/sessions/{id}/self-assess")
    public GrammarDtos.ResultView selfAssess(@PathVariable long id, @RequestBody GrammarDtos.SelfAssessRequest req) {
        return exercises.selfAssess(id, req);
    }

    @GetMapping("/sessions/{id}/result")
    public GrammarDtos.ResultView result(@PathVariable long id) {
        return exercises.result(id);
    }
}
