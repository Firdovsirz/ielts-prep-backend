package com.ieltsprep.vocab;

import com.ieltsprep.claude.ClaudeException;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.generation.GenerationRequest;
import com.ieltsprep.generation.GenerationService;
import com.ieltsprep.grading.GradingDispatcher;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vocab")
@Tag(name = "Vocabulary")
public class VocabController {

    public record Queued(String message) {}

    private final VocabService vocab;
    private final GenerationService generation;
    private final GradingDispatcher dispatcher;
    private final ClaudeService claude;

    public VocabController(VocabService vocab, GenerationService generation, GradingDispatcher dispatcher, ClaudeService claude) {
        this.vocab = vocab;
        this.generation = generation;
        this.dispatcher = dispatcher;
        this.claude = claude;
    }

    @GetMapping("/overview")
    public VocabDtos.Overview overview() {
        return vocab.overview();
    }

    @GetMapping("/due")
    public List<VocabDtos.CardView> due(@RequestParam(defaultValue = "30") int limit) {
        return vocab.due(limit);
    }

    @GetMapping("/cards")
    public List<VocabDtos.CardView> cards() {
        return vocab.list();
    }

    @PostMapping("/cards")
    public VocabDtos.CardView add(@Valid @RequestBody VocabDtos.AddRequest req) {
        return vocab.add(req);
    }

    /** Captures a word flagged in a Listening transcript or Reading passage review. */
    @PostMapping("/capture")
    public VocabDtos.Captured capture(@Valid @RequestBody VocabDtos.AddRequest req) {
        return new VocabDtos.Captured(VocabService.normalise(req.word()),
                vocab.captureWord(req.word(), req.sentence(), req.source() == null ? "FLAG" : req.source(), null));
    }

    @PostMapping("/cards/{id}/review")
    public VocabDtos.CardView review(@PathVariable long id, @RequestBody VocabDtos.ReviewRequest req) {
        return vocab.review(id, req.grade());
    }

    @PutMapping("/cards/{id}")
    public VocabDtos.CardView edit(@PathVariable long id, @RequestBody VocabDtos.EditRequest req) {
        return vocab.edit(id, req);
    }

    @DeleteMapping("/cards/{id}")
    public void remove(@PathVariable long id) {
        vocab.remove(id);
    }

    @GetMapping("/banks")
    public List<VocabDtos.BankSummary> banks() {
        return vocab.banks();
    }

    @GetMapping("/banks/{itemId}")
    public VocabDtos.BankView bank(@PathVariable long itemId) {
        return vocab.bank(itemId);
    }

    @PostMapping("/banks/{itemId}/add")
    public VocabDtos.Added addBank(@PathVariable long itemId) {
        return vocab.addBank(itemId);
    }

    @PostMapping("/banks/generate/{topic}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Queued generateBank(@PathVariable String topic) {
        if (!claude.isAvailable()) {
            throw ClaudeException.notConfigured();
        }
        dispatcher.dispatch("generate word bank " + topic, () -> generation.generate(GenerationRequest.of(TaskType.VOCAB_WORD_BANK, topic, false)));
        return new Queued("A new " + topic + " word bank is being written and reviewed; it will appear in about a minute.");
    }
}
