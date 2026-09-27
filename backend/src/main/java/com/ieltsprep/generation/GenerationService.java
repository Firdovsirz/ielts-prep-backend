package com.ieltsprep.generation;

import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemFactory;
import com.ieltsprep.content.ItemOrigin;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.ItemValidator;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two-pass generation: one Claude call writes the item, deterministic checks ({@link ItemValidator}) run, then an
 * independent Claude call re-answers the questions blind / reviews the item. Failures are regenerated with the
 * verifier's objections (at most {@value #MAX_ATTEMPTS} attempts in total). Only VERIFIED items are ever served;
 * the last failed draft is kept as FAILED for the record.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);
    static final int MAX_ATTEMPTS = 3;

    public record Outcome(Item item, boolean verified, int attempts, List<String> history) {}

    private final ClaudeService claude;
    private final BlueprintRegistry registry;
    private final ItemValidator validator;
    private final ItemFactory factory;
    private final ItemRepository items;
    private final TransactionTemplate tx;

    public GenerationService(ClaudeService claude, BlueprintRegistry registry, ItemValidator validator, ItemFactory factory,
            ItemRepository items, TransactionTemplate tx) {
        this.claude = claude;
        this.registry = registry;
        this.validator = validator;
        this.factory = factory;
        this.items = items;
        this.tx = tx;
    }

    public Outcome generate(GenerationRequest request) {
        return generate(request, MAX_ATTEMPTS);
    }

    public Outcome generate(GenerationRequest request, int maxAttempts) {
        Blueprint blueprint = registry.get(request.type());
        TaskType type = request.type();
        GenerationPlan plan = blueprint.plan(request);
        List<String> history = new ArrayList<>();
        String feedback = request.feedback();
        Object lastContent = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Map<String, Object> vars = new HashMap<>(plan.vars());
            vars.put("feedback", feedbackBlock(feedback));
            ClaudeCall<?> genCall = ClaudeCall.of(plan.prompt(), vars, type.contentType()).ref("generate " + type + " #" + attempt);
            if (request.background()) {
                genCall = genCall.inBackground();
            }
            Object content = claude.call(genCall);
            lastContent = content;

            ItemValidator.Report structural = validator.validate(type, content);
            if (!structural.ok()) {
                feedback = "Structural problems found by the automatic checker:\n- " + String.join("\n- ", structural.errors());
                history.add("attempt " + attempt + " failed structural checks: " + structural.summary());
                log.info("{} attempt {} failed structural checks: {}", type, attempt, structural.summary());
                continue;
            }

            ClaudeCall<VerificationReport> verifyCall = ClaudeCall.of(blueprint.verifyPrompt(), blueprint.verifyVars(content),
                    VerificationReport.class).ref("verify " + type + " #" + attempt);
            VerificationReport report = claude.call(request.background() ? verifyCall.inBackground() : verifyCall);
            Verdict verdict = blueprint.judge(content, report);
            if (verdict.pass()) {
                String notes = "Verified on attempt " + attempt + ". " + verdict.notes()
                        + (structural.warnings().isEmpty() ? "" : " Warnings: " + String.join("; ", structural.warnings()));
                history.add("attempt " + attempt + " verified");
                Item saved = save(type, content, plan, VerificationStatus.VERIFIED, notes, attempt);
                log.info("Generated and verified {} item {} ({}) on attempt {}", type, saved.getId(), saved.getTitle(), attempt);
                return new Outcome(saved, true, attempt, history);
            }
            feedback = String.join("\n", verdict.reasons());
            history.add("attempt " + attempt + " failed verification: " + String.join(" | ", verdict.reasons()));
            log.info("{} attempt {} failed verification: {}", type, attempt, verdict.reasons());
        }

        Item failed = lastContent == null ? null
                : save(type, lastContent, plan, VerificationStatus.FAILED, String.join("\n", history), maxAttempts);
        return new Outcome(failed, false, maxAttempts, history);
    }

    private Item save(TaskType type, Object content, GenerationPlan plan, VerificationStatus status, String notes, int attempts) {
        return tx.execute(s -> {
            Item item = factory.create(type, content, plan.sourceUrl(), plan.licence(), ItemOrigin.GENERATED);
            item.setVerificationStatus(status);
            item.setVerificationNotes(notes);
            item.setGenerationAttempts(attempts);
            return items.save(item);
        });
    }

    static String feedbackBlock(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return "";
        }
        return "IMPORTANT — a previous draft of this item was rejected by an independent checker. Keep what worked, "
                + "but fix every one of these problems:\n" + feedback;
    }

    /** Debug helper for the CLI: the rendered plan without calling Claude. */
    public String describe(GenerationRequest request) {
        GenerationPlan plan = registry.get(request.type()).plan(request);
        return plan.prompt() + " " + Json.write(plan.vars());
    }
}
