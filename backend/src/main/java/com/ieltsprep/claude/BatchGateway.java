package com.ieltsprep.claude;

import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Message Batches API (50% price, asynchronous): submit prepared requests, poll status, stream results. Each
 * succeeded result is logged to api_usage at batch pricing.
 */
@Component
public class BatchGateway {

    public record Entry(String customId, PreparedRequest request) {}

    public record Result(String customId, boolean succeeded, String text, String stopReason, String error) {}

    private final ClaudeService claude;
    private final UsageLogger usage;
    private final SpendGuard spendGuard;

    public BatchGateway(ClaudeService claude, UsageLogger usage, SpendGuard spendGuard) {
        this.claude = claude;
        this.usage = usage;
        this.spendGuard = spendGuard;
    }

    public String submit(List<Entry> entries) {
        if (!claude.isAvailable()) {
            throw ClaudeException.notConfigured();
        }
        spendGuard.ensureAllowed(true);
        BatchCreateParams.Builder params = BatchCreateParams.builder();
        entries.forEach(e -> params.addRequest(e.request().toBatchRequest(e.customId())));
        MessageBatch batch = claude.client().messages().batches().create(params.build());
        return batch.id();
    }

    /** "in_progress", "canceling" or "ended". */
    public String status(String batchId) {
        return claude.client().messages().batches().retrieve(batchId).processingStatus().asString();
    }

    public List<Result> results(String batchId, String purpose) {
        List<Result> out = new ArrayList<>();
        try (StreamResponse<MessageBatchIndividualResponse> stream = claude.client().messages().batches().resultsStreaming(batchId)) {
            stream.stream().forEach(r -> {
                if (r.result().isSucceeded()) {
                    Message m = r.result().asSucceeded().message();
                    Usage u = m.usage();
                    usage.record(m.model().asString(), purpose, r.customId(), u.inputTokens(), u.outputTokens(),
                            u.cacheReadInputTokens().orElse(0L), u.cacheCreationInputTokens().orElse(0L), true, true, null, null);
                    out.add(new Result(r.customId(), true, ClaudeService.text(m),
                            m.stopReason().map(Object::toString).orElse(""), null));
                } else {
                    String error = r.result().errored().map(e -> e.error().toString()).orElse(String.valueOf(r.result()));
                    out.add(new Result(r.customId(), false, null, null, error));
                }
            });
        }
        return out;
    }
}
