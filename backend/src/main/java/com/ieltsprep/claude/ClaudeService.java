package com.ieltsprep.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.SseException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * The only place the Anthropic SDK is called for interactive requests. Every call:
 * <ul>
 *   <li>renders a prompt file from {@code /prompts} and constrains the reply with its JSON schema
 *       ({@code output_config.format}), deserialising into a Java record;</li>
 *   <li>puts the static reference documents + system instructions first with a cache breakpoint (prompt caching);</li>
 *   <li>checks the daily spend cap, retries transient failures with exponential backoff, and logs tokens + cost.</li>
 * </ul>
 * Callers must not hold a database transaction open across a call.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private final ClaudeProperties props;
    private final PromptRepository prompts;
    private final SchemaRepository schemas;
    private final ReferenceLibrary references;
    private final SpendGuard spendGuard;
    private final UsageLogger usageLogger;
    private final RetryTemplate retry;
    private final ApiKeyStore keys;
    private volatile AnthropicClient client;
    private volatile long clientKeyVersion = -1;

    public ClaudeService(ClaudeProperties props, PromptRepository prompts, SchemaRepository schemas,
            ReferenceLibrary references, SpendGuard spendGuard, UsageLogger usageLogger, ApiKeyStore keys) {
        this.props = props;
        this.keys = keys;
        this.prompts = prompts;
        this.schemas = schemas;
        this.references = references;
        this.spendGuard = spendGuard;
        this.usageLogger = usageLogger;
        this.retry = RetryTemplate.builder()
                .maxAttempts(Math.max(1, props.maxAttempts()))
                .exponentialBackoff(Math.max(100, props.initialBackoffMs()), 2.0, 60_000)
                .retryOn(ClaudeException.Retryable.class)
                .build();
    }

    /** True when an API key is configured. Features degrade gracefully (seed content, self-marking) when false. */
    public boolean isAvailable() {
        return keys.present();
    }

    /**
     * Checks a key with Anthropic before it is saved (listing models is free). Throws ApiException with a readable
     * message when the key is rejected or Anthropic cannot be reached.
     */
    public void verifyKey(String key) {
        AnthropicClient probe = builder(key).build();
        try {
            probe.models().list(com.anthropic.models.models.ModelListParams.builder().limit(1L).build());
        } catch (com.anthropic.errors.UnauthorizedException | com.anthropic.errors.PermissionDeniedException e) {
            throw com.ieltsprep.common.ApiException.badRequest("Anthropic rejected this key — check that it is copied completely and still active.");
        } catch (com.anthropic.errors.AnthropicIoException e) {
            throw com.ieltsprep.common.ApiException.badRequest("Could not reach Anthropic to verify the key: " + e.getMessage());
        } finally {
            probe.close();
        }
    }

    public <T> T call(ClaudeCall<T> call) {
        return execute(call).value();
    }

    public <T> ClaudeResult<T> execute(ClaudeCall<T> call) {
        if (!isAvailable()) {
            throw ClaudeException.notConfigured();
        }
        spendGuard.ensureAllowed(call.background());
        PreparedRequest request = prepare(call);
        return retry.execute(ctx -> {
            if (ctx.getRetryCount() > 0) {
                log.info("Retrying {} (attempt {})", request.purpose(), ctx.getRetryCount() + 1);
            }
            return send(request, call);
        });
    }

    /** Renders the prompt into SDK params (also used by the batch service). */
    public PreparedRequest prepare(ClaudeCall<?> call) {
        PromptTemplate template = prompts.get(call.prompt());
        ModelRoute route = template.route();
        String model = props.model(route);

        List<TextBlockParam> system = new ArrayList<>();
        for (String file : template.context()) {
            system.add(TextBlockParam.builder()
                    .text("<reference path=\"data/" + file + "\">\n" + references.text(file) + "\n</reference>")
                    .build());
        }
        system.add(TextBlockParam.builder().text(template.renderSystem(call.vars())).build());
        // Cache breakpoint on the last static block: references + instructions are identical across calls.
        TextBlockParam last = system.removeLast();
        system.add(last.toBuilder().cacheControl(cacheControl()).build());

        List<MessageParam> messages = new ArrayList<>();
        for (ClaudeCall.Turn turn : call.history()) {
            messages.add(MessageParam.builder()
                    .role("assistant".equals(turn.role()) ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER)
                    .content(turn.text())
                    .build());
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(template.renderUser(call.vars())).build());

        OutputConfig.Builder output = OutputConfig.builder();
        if (template.schema() != null) {
            Map<String, JsonValue> schema = schemas.get(template.schema()).entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonValue.from(e.getValue())));
            output.format(JsonOutputFormat.builder()
                    .schema(JsonOutputFormat.Schema.builder().putAllAdditionalProperties(schema).build())
                    .build());
        }
        String effort = props.effort(route);
        if (effort != null) {
            output.effort(OutputConfig.Effort.of(effort));
        }
        long maxTokens = template.maxTokens() != null ? template.maxTokens() : props.maxTokens(route);
        return new PreparedRequest(call.prompt(), model, maxTokens, system, messages, output.build());
    }

    private <T> ClaudeResult<T> send(PreparedRequest request, ClaudeCall<T> call) {
        long started = System.currentTimeMillis();
        Message message;
        try {
            MessageAccumulator accumulator = MessageAccumulator.create();
            try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(request.toMessageParams())) {
                stream.stream().forEach(accumulator::accumulate);
            }
            message = accumulator.message();
        } catch (AnthropicServiceException e) {
            logFailure(request, call, e, started);
            int status = e.statusCode();
            if (e instanceof SseException || status == 408 || status == 409 || status == 429 || status >= 500) {
                throw new ClaudeException.Retryable("Claude API " + status + ": " + e.getMessage(), e);
            }
            if (e instanceof UnauthorizedException) {
                throw new ClaudeException("CLAUDE_AUTH", "The Anthropic API key was rejected. Check ANTHROPIC_API_KEY.", e);
            }
            throw new ClaudeException("CLAUDE_REQUEST_ERROR", "Claude API " + status + ": " + e.getMessage(), e);
        } catch (AnthropicIoException | AnthropicRetryableException e) {
            logFailure(request, call, e, started);
            throw new ClaudeException.Retryable("Claude API connection problem: " + e.getMessage(), e);
        }

        Usage usage = message.usage();
        long cacheRead = usage.cacheReadInputTokens().orElse(0L);
        long cacheWrite = usage.cacheCreationInputTokens().orElse(0L);
        StopReason stop = message.stopReason().orElse(null);
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());

        if (StopReason.REFUSAL.equals(stop) || StopReason.MAX_TOKENS.equals(stop) || text.isBlank()) {
            String reason = StopReason.REFUSAL.equals(stop) ? "Claude declined the request"
                    : StopReason.MAX_TOKENS.equals(stop) ? "Response hit max_tokens (" + request.maxTokens() + ")"
                    : "Empty response";
            usageLogger.record(request.model(), request.purpose(), call.ref(), usage.inputTokens(), usage.outputTokens(),
                    cacheRead, cacheWrite, false, false, reason, System.currentTimeMillis() - started);
            throw new ClaudeException(StopReason.REFUSAL.equals(stop) ? "CLAUDE_REFUSAL" : "CLAUDE_INCOMPLETE", reason);
        }

        BigDecimal cost = usageLogger.record(request.model(), request.purpose(), call.ref(), usage.inputTokens(),
                usage.outputTokens(), cacheRead, cacheWrite, false, true, null, System.currentTimeMillis() - started);
        T value = parse(text, call.type());
        return new ClaudeResult<>(value, text, request.model(), usage.inputTokens(), usage.outputTokens(), cost);
    }

    /** Parses a structured-output JSON document into the target record. */
    @SuppressWarnings("unchecked")
    public static <T> T parse(String json, Class<T> type) {
        if (type == JsonNode.class) {
            return (T) Json.tree(json);
        }
        if (type == String.class) {
            return (T) json;
        }
        try {
            return Json.MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new ClaudeException("CLAUDE_BAD_OUTPUT", "Could not map Claude output to " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** Text of the first text block, for batch results. */
    public static String text(Message message) {
        return message.content().stream()
                .flatMap((ContentBlock block) -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
    }

    /** SDK client for the batch gateway (same package). Rebuilt when the API key changes. */
    AnthropicClient client() {
        long v = keys.version();
        AnthropicClient c = client;
        if (c == null || clientKeyVersion != v) {
            synchronized (this) {
                if (client == null || clientKeyVersion != v) {
                    String key = keys.current().orElseThrow(ClaudeException::notConfigured);
                    client = builder(key).build(); // the previous client is left to finish any call in flight
                    clientKeyVersion = v;
                }
                c = client;
            }
        }
        return c;
    }

    private AnthropicOkHttpClient.Builder builder(String key) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(key.trim())
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .maxRetries(0); // retries are handled by the RetryTemplate above
        if (props.baseUrl() != null && !props.baseUrl().isBlank()) {
            builder.baseUrl(props.baseUrl().trim());
        }
        return builder;
    }

    private void logFailure(PreparedRequest request, ClaudeCall<?> call, Exception e, long started) {
        usageLogger.record(request.model(), request.purpose(), call.ref(), 0, 0, 0, 0, false, false,
                e.getClass().getSimpleName() + ": " + e.getMessage(), System.currentTimeMillis() - started);
    }

    private CacheControlEphemeral cacheControl() {
        CacheControlEphemeral.Builder cc = CacheControlEphemeral.builder();
        if (props.longCacheTtl()) {
            cc.ttl(CacheControlEphemeral.Ttl.TTL_1H);
        }
        return cc.build();
    }
}
