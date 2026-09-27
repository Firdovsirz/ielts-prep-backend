package com.ieltsprep.generation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.claude.BatchGateway;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.claude.SpendGuard;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemFactory;
import com.ieltsprep.content.ItemOrigin;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.ItemValidator;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk pre-generation through the Message Batches API with the same two-pass verification: a GENERATE batch
 * produces drafts (stored PENDING, never served), a VERIFY batch re-answers them blind, passing items become VERIFIED
 * and failing ones are regenerated interactively with the verifier's feedback (max 2 retries).
 */
@Service
public class BatchGenerationService {

    private static final Logger log = LoggerFactory.getLogger(BatchGenerationService.class);

    /** Rough batch cost per item incl. its verification, used to size batches to the remaining budget. */
    private static final Map<TaskType, BigDecimal> ESTIMATE = Map.of(
            TaskType.READING_PASSAGE, new BigDecimal("0.07"), TaskType.LISTENING_SECTION, new BigDecimal("0.07"),
            TaskType.GRAMMAR_EXERCISE, new BigDecimal("0.02"), TaskType.VOCAB_WORD_BANK, new BigDecimal("0.02"));

    public record Submitted(long jobId, String batchId, int requests) {}

    private final BatchGateway gateway;
    private final ClaudeService claude;
    private final BlueprintRegistry registry;
    private final ItemValidator validator;
    private final ItemFactory factory;
    private final ItemRepository items;
    private final BatchJobRepository jobs;
    private final GenerationService generation;
    private final SpendGuard spendGuard;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BatchGenerationService(BatchGateway gateway, ClaudeService claude, BlueprintRegistry registry, ItemValidator validator,
            ItemFactory factory, ItemRepository items, BatchJobRepository jobs, GenerationService generation, SpendGuard spendGuard,
            TransactionTemplate tx, Clock clock) {
        this.gateway = gateway;
        this.claude = claude;
        this.registry = registry;
        this.validator = validator;
        this.factory = factory;
        this.items = items;
        this.jobs = jobs;
        this.generation = generation;
        this.spendGuard = spendGuard;
        this.tx = tx;
        this.clock = clock;
    }

    public Submitted submitGeneration(TaskType type, String variant, int requested) {
        Blueprint bp = registry.get(type);
        BigDecimal perItem = ESTIMATE.getOrDefault(type, new BigDecimal("0.01"));
        int affordable = spendGuard.remaining(true).divide(perItem, 0, RoundingMode.DOWN).intValue();
        int count = Math.min(requested, Math.max(0, affordable));
        if (count == 0) {
            throw new IllegalStateException("Not enough background budget left today for " + type + " (≈$" + perItem + " each).");
        }
        List<String> variants = bp.bufferVariants().stream().filter(v -> v != null).toList();
        List<BatchGateway.Entry> entries = new ArrayList<>();
        Map<String, Map<String, String>> details = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String v = variant != null ? variant : variants.isEmpty() ? null : variants.get(i % variants.size());
            GenerationPlan plan = bp.plan(GenerationRequest.of(type, v, true));
            Map<String, Object> vars = new HashMap<>(plan.vars());
            vars.put("feedback", "");
            String customId = "gen-" + i;
            entries.add(new BatchGateway.Entry(customId, claude.prepare(ClaudeCall.of(plan.prompt(), vars, type.contentType()))));
            Map<String, String> d = new HashMap<>();
            d.put("variant", v == null ? "" : v);
            d.put("source_url", plan.sourceUrl() == null ? "" : plan.sourceUrl());
            d.put("licence", plan.licence() == null ? "" : plan.licence());
            details.put(customId, d);
        }
        String batchId = gateway.submit(entries);
        BatchJob job = saveJob(batchId, "GENERATE", type, entries.size(), Json.write(details));
        log.info("Submitted GENERATE batch {} ({} × {})", batchId, entries.size(), type);
        return new Submitted(job.getId(), batchId, entries.size());
    }

    @Scheduled(initialDelay = 3, fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void scheduledPoll() {
        if (claude.isAvailable()) {
            try {
                pollOpenJobs();
            } catch (Exception e) {
                log.warn("Batch poll failed: {}", e.getMessage());
            }
        }
    }

    /** Processes every finished job; returns how many jobs are still in progress. */
    public int pollOpenJobs() {
        int open = 0;
        for (BatchJob job : jobs.findByStatusOrderByIdAsc("IN_PROGRESS")) {
            String status = gateway.status(job.getAnthropicBatchId());
            if (!"ended".equals(status)) {
                open++;
                continue;
            }
            if ("GENERATE".equals(job.getStage())) {
                open += processGenerate(job) ? 1 : 0;
            } else {
                processVerify(job);
            }
            job.setStatus("DONE");
            job.setFinishedAt(Instant.now(clock));
            jobs.save(job);
        }
        return open;
    }

    /** Blocks (CLI) until every open job is processed. */
    public void awaitAll(Duration pollEvery, java.util.function.Consumer<String> progress) throws InterruptedException {
        while (true) {
            int open = pollOpenJobs();
            if (open == 0) {
                return;
            }
            progress.accept(open + " batch job(s) still processing…");
            Thread.sleep(pollEvery.toMillis());
        }
    }

    private boolean processGenerate(BatchJob job) {
        TaskType type = TaskType.valueOf(job.getTaskType());
        Blueprint bp = registry.get(type);
        Map<String, Map<String, String>> details = Json.read(job.getDetails(), new TypeReference<>() {});
        List<BatchGateway.Entry> verify = new ArrayList<>();
        Map<String, String> verifyDetails = new LinkedHashMap<>();
        for (BatchGateway.Result r : gateway.results(job.getAnthropicBatchId(), "batch generate " + type)) {
            Map<String, String> d = details.getOrDefault(r.customId(), Map.of());
            if (!r.succeeded()) {
                log.warn("Batch item {} errored: {}", r.customId(), r.error());
                continue;
            }
            Object content;
            try {
                content = ClaudeService.parse(r.text(), type.contentType());
            } catch (Exception e) {
                log.warn("Batch item {} unparseable: {}", r.customId(), e.getMessage());
                continue;
            }
            ItemValidator.Report report = validator.validate(type, content);
            if (!report.ok()) {
                regenerate(type, emptyToNull(d.get("variant")), "Structural problems found by the automatic checker:\n- "
                        + String.join("\n- ", report.errors()));
                continue;
            }
            Item item = tx.execute(s -> {
                Item it = factory.create(type, content, d.get("source_url"), d.get("licence"), ItemOrigin.BATCH);
                it.setVerificationStatus(VerificationStatus.PENDING);
                it.setGenerationAttempts(1);
                return items.save(it);
            });
            String customId = "ver-" + item.getId();
            verify.add(new BatchGateway.Entry(customId,
                    claude.prepare(ClaudeCall.of(bp.verifyPrompt(), bp.verifyVars(content), VerificationReport.class))));
            verifyDetails.put(customId, String.valueOf(item.getId()));
        }
        if (verify.isEmpty()) {
            return false;
        }
        String batchId = gateway.submit(verify);
        saveJob(batchId, "VERIFY", type, verify.size(), Json.write(verifyDetails));
        log.info("Submitted VERIFY batch {} for {} drafts", batchId, verify.size());
        return true;
    }

    private void processVerify(BatchJob job) {
        TaskType type = TaskType.valueOf(job.getTaskType());
        Blueprint bp = registry.get(type);
        Map<String, String> details = Json.read(job.getDetails(), new TypeReference<>() {});
        for (BatchGateway.Result r : gateway.results(job.getAnthropicBatchId(), "batch verify " + type)) {
            Long itemId = Long.valueOf(details.get(r.customId()));
            Item item = items.findById(itemId).orElse(null);
            if (item == null) {
                continue;
            }
            Object content = item.contentAs(type.contentType());
            Verdict verdict;
            if (!r.succeeded()) {
                verdict = Verdict.fail(List.of("verification request failed: " + r.error()), "");
            } else {
                verdict = bp.judge(content, ClaudeService.parse(r.text(), VerificationReport.class));
            }
            tx.executeWithoutResult(s -> {
                Item it = items.findById(itemId).orElseThrow();
                it.setVerificationStatus(verdict.pass() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
                it.setVerificationNotes((verdict.pass() ? "Verified by batch blind check. " : "Failed batch blind check: "
                        + String.join(" | ", verdict.reasons()) + ". ") + verdict.notes());
                items.save(it);
            });
            if (!verdict.pass()) {
                regenerate(type, item.getVariant(), String.join("\n", verdict.reasons()));
            }
        }
    }

    /** Interactive regeneration of a failed batch draft (max 2 retries), fed the verifier's objections. */
    private void regenerate(TaskType type, String variant, String feedback) {
        try {
            generation.generate(new GenerationRequest(type, variant, true, Map.of(), feedback), 2);
        } catch (Exception e) {
            log.warn("Regeneration of {} failed: {}", type, e.getMessage());
        }
    }

    private BatchJob saveJob(String batchId, String stage, TaskType type, int count, String details) {
        BatchJob job = new BatchJob();
        job.setAnthropicBatchId(batchId);
        job.setStage(stage);
        job.setTaskType(type.name());
        job.setRequestCount(count);
        job.setStatus("IN_PROGRESS");
        job.setDetails(details);
        job.setCreatedAt(Instant.now(clock));
        return jobs.save(job);
    }

    public List<BatchJob> recent() {
        return jobs.findTop20ByOrderByIdDesc();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
