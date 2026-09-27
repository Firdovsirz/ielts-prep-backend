package com.ieltsprep.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemFactory;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.ItemValidator;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.reading.ReadingPassage;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Two-pass generation with a mocked ClaudeService: no API key, no network. */
class GenerationServiceTest {

    ClaudeService claude = mock(ClaudeService.class);
    ItemRepository items = mock(ItemRepository.class);
    TransactionTemplate tx = mock(TransactionTemplate.class);
    GenerationService service;
    ReadingPassage passage;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        JsonNode seed = Json.MAPPER.readTree(Path.of("../data/seed/reading/p1-01.json").toFile());
        passage = Json.MAPPER.treeToValue(seed.get("content"), ReadingPassage.class);
        Blueprint blueprint = new Blueprint() {
            @Override
            public TaskType type() {
                return TaskType.READING_PASSAGE;
            }

            @Override
            public List<String> bufferVariants() {
                return List.of("P1");
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                return new GenerationPlan("reading-generate", Map.of("topic_title", "Honeyguides"), "https://en.wikipedia.org/wiki/Greater_honeyguide", "CC BY-SA 4.0", "test");
            }

            @Override
            public String verifyPrompt() {
                return "reading-verify";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return Map.of("exam_paper", ExamPaperRenderer.reading(((ReadingPassage) content).withoutAnswers()));
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return QuestionJudge.judge(((ReadingPassage) content).questionGroups(), report);
            }
        };
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Item>) inv.getArgument(0)).doInTransaction(null));
        when(items.save(any(Item.class))).thenAnswer(inv -> {
            Item i = inv.getArgument(0);
            i.setId(42L);
            return i;
        });
        service = new GenerationService(claude, new BlueprintRegistry(List.of(blueprint)), new ItemValidator(),
                new ItemFactory(Clock.systemUTC()), items, tx);
    }

    /** Answers generation calls with the seed passage and verification calls with the queued reports in order. */
    private void stub(VerificationReport... reports) {
        java.util.ArrayDeque<VerificationReport> queue = new java.util.ArrayDeque<>(List.of(reports));
        when(claude.call(any())).thenAnswer(inv -> {
            ClaudeCall<?> c = inv.getArgument(0);
            if (c.prompt().equals("reading-generate")) {
                return passage;
            }
            return queue.size() > 1 ? queue.poll() : queue.peek();
        });
    }

    private VerificationReport report(boolean allCorrect) {
        List<VerificationReport.BlindAnswer> blind = new ArrayList<>();
        passage.questionGroups().forEach(g -> g.questions().forEach(q -> blind.add(new VerificationReport.BlindAnswer(
                String.valueOf(q.number()), allCorrect || q.number() != 3 ? q.answers().getFirst() : "WRONG", "high", "quote"))));
        return new VerificationReport(blind, List.of(), allCorrect ? "PASS" : "FAIL", "checked");
    }

    @Test
    void itemThatPassesBlindVerificationIsSavedAsVerified() {
        stub(report(true));

        GenerationService.Outcome outcome = service.generate(GenerationRequest.of(TaskType.READING_PASSAGE, "P1", false));

        assertThat(outcome.verified()).isTrue();
        assertThat(outcome.attempts()).isEqualTo(1);
        assertThat(outcome.item().getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(outcome.item().getVariant()).isEqualTo("P1");
        assertThat(outcome.item().getSourceUrl()).contains("wikipedia");
        assertThat(outcome.item().getAnswerKey()).contains("justification_span");
    }

    @Test
    void disagreementWithTheKeyTriggersRegenerationWithFeedback() {
        stub(report(false), report(true));

        GenerationService.Outcome outcome = service.generate(GenerationRequest.of(TaskType.READING_PASSAGE, "P1", true));

        assertThat(outcome.verified()).isTrue();
        assertThat(outcome.attempts()).isEqualTo(2);
        assertThat(outcome.history().getFirst()).contains("Q3");
        // the second generation call carries the verifier's objection as feedback
        verify(claude).call(argThat((ClaudeCall<?> c) -> c != null && c.prompt().equals("reading-generate")
                && String.valueOf(c.vars().get("feedback")).contains("Q3") && c.background()));
    }

    @Test
    void itemsThatNeverVerifyAreStoredAsFailedAndNeverServed() {
        stub(report(false));

        GenerationService.Outcome outcome = service.generate(GenerationRequest.of(TaskType.READING_PASSAGE, "P1", false));

        assertThat(outcome.verified()).isFalse();
        assertThat(outcome.attempts()).isEqualTo(GenerationService.MAX_ATTEMPTS);
        assertThat(outcome.item().getVerificationStatus()).isEqualTo(VerificationStatus.FAILED);
        verify(claude, times(3)).call(argThat((ClaudeCall<?> c) -> c != null && c.prompt().equals("reading-verify")));
    }
}
