package com.ieltsprep.vocab;

import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.TopicTaxonomy;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.generation.Blueprint;
import com.ieltsprep.generation.GenerationPlan;
import com.ieltsprep.generation.GenerationRequest;
import com.ieltsprep.generation.VerificationReport;
import com.ieltsprep.generation.Verdict;
import com.ieltsprep.common.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Topic word banks: generated on demand, reviewed by the content reviewer (not buffered). */
@Component
public class VocabBlueprint implements Blueprint {

    private final ItemRepository items;
    private final TopicTaxonomy topics;

    public VocabBlueprint(ItemRepository items, TopicTaxonomy topics) {
        this.items = items;
        this.topics = topics;
    }

    @Override
    public TaskType type() {
        return TaskType.VOCAB_WORD_BANK;
    }

    @Override
    public List<String> bufferVariants() {
        return List.of();
    }

    @Override
    public GenerationPlan plan(GenerationRequest request) {
        String topic = request.variant() == null ? topics.rotation().getFirst() : request.variant();
        List<String> existing = new ArrayList<>();
        items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.VOCAB_WORD_BANK, topic, VerificationStatus.VERIFIED)
                .forEach(i -> i.contentAs(WordBank.class).words().forEach(w -> existing.add(w.word())));
        Map<String, Object> vars = new HashMap<>();
        vars.put("topic", topic);
        vars.put("subtopics", String.join(", ", topics.topics().getOrDefault(topic, new TopicTaxonomy.Topic(topic, topic, List.of())).subtopics()));
        vars.put("existing", existing.isEmpty() ? "none" : String.join(", ", existing));
        return new GenerationPlan("vocab-word-bank-generate", vars, null, "Original content", "Word bank " + topic);
    }

    @Override
    public String verifyPrompt() {
        return "content-review";
    }

    @Override
    public Map<String, Object> verifyVars(Object content) {
        return Map.of("item_kind", "IELTS topic vocabulary bank", "content_json", Json.pretty(content), "checklist", """
                - Definitions are accurate for the sense used; examples are natural and use the word correctly.
                - Collocations are genuinely common in English (no invented combinations).
                - CEFR levels are plausible; items are useful for Writing Task 2 / Speaking Part 3 on this topic.""");
    }

    @Override
    public Verdict judge(Object content, VerificationReport report) {
        List<String> reasons = report.blockers().stream().map(i -> i.kind() + ": " + i.detail()).collect(Collectors.toList());
        if (!report.passed() && reasons.isEmpty()) {
            reasons.add("Reviewer verdict FAIL: " + report.summary());
        }
        return reasons.isEmpty() ? Verdict.pass(report.summary()) : Verdict.fail(reasons, report.summary());
    }
}
