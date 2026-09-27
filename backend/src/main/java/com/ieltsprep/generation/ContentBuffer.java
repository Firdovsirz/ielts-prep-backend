package com.ieltsprep.generation;

import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.claude.SpendCapExceededException;
import com.ieltsprep.config.AppProperties;
import com.ieltsprep.content.ExamType;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.settings.SettingsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps at least {@code ielts.buffer.min-per-bucket} verified, not-yet-seen items in every bucket (Reading P1–P3,
 * Listening S1–S4, Task 1/Task 2 prompts, Speaking parts, grammar exercises) so a practice session never waits on
 * generation. Runs in the background within the background share of the daily spend cap.
 */
@Component
public class ContentBuffer {

    private static final Logger log = LoggerFactory.getLogger(ContentBuffer.class);

    public record Bucket(TaskType type, String variant, long unserved, int target) {
        public long deficit() {
            return Math.max(0, target - unserved);
        }
    }

    public record Status(boolean enabled, boolean running, Instant lastRun, String lastMessage, List<Bucket> buckets) {}

    private final AppProperties props;
    private final ClaudeService claude;
    private final BlueprintRegistry registry;
    private final GenerationService generation;
    private final ItemRepository items;
    private final SettingsService settings;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastRun;
    private volatile String lastMessage = "Not run yet";

    public ContentBuffer(AppProperties props, ClaudeService claude, BlueprintRegistry registry, GenerationService generation,
            ItemRepository items, SettingsService settings) {
        this.props = props;
        this.claude = claude;
        this.registry = registry;
        this.generation = generation;
        this.items = items;
        this.settings = settings;
    }

    @Scheduled(initialDelayString = "${ielts.buffer.initial-delay-minutes:2}", fixedDelayString = "${ielts.buffer.interval-minutes:30}",
            timeUnit = TimeUnit.MINUTES)
    public void scheduledTopUp() {
        if (props.buffer() != null && props.buffer().enabled()) {
            topUp(Integer.MAX_VALUE);
        }
    }

    /** Generates until every bucket meets its target (or {@code maxItems} were produced, or the cap is hit). */
    public int topUp(int maxItems) {
        if (!claude.isAvailable()) {
            lastMessage = "Skipped: no API key configured";
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        int produced = 0;
        try {
            while (produced < maxItems) {
                Bucket next = buckets().stream().filter(b -> b.deficit() > 0)
                        .min(Comparator.comparingLong(Bucket::unserved)).orElse(null);
                if (next == null) {
                    lastMessage = "All buckets full";
                    break;
                }
                GenerationService.Outcome outcome = generation.generate(GenerationRequest.of(next.type(), next.variant(), true));
                produced++;
                if (!outcome.verified()) {
                    lastMessage = "Last item for " + next.type() + " failed verification";
                    // avoid hammering one bucket that keeps failing in this run
                    break;
                }
                lastMessage = "Generated " + next.type() + (next.variant() == null ? "" : " " + next.variant());
            }
        } catch (SpendCapExceededException e) {
            lastMessage = e.getMessage();
            log.info("Buffer top-up paused: {}", e.getMessage());
        } catch (Exception e) {
            lastMessage = "Error: " + e.getMessage();
            log.warn("Buffer top-up failed", e);
        } finally {
            lastRun = Instant.now();
            running.set(false);
        }
        return produced;
    }

    public List<Bucket> buckets() {
        int target = props.buffer() == null ? 5 : props.buffer().minPerBucket();
        ExamType exam = settings.get().getExamType();
        List<Bucket> out = new ArrayList<>();
        for (Blueprint bp : registry.all()) {
            if (bp.type() == TaskType.WRITING_TASK1_GENERAL && exam == ExamType.ACADEMIC
                    || bp.type() == TaskType.WRITING_TASK1_ACADEMIC && exam == ExamType.GENERAL) {
                continue; // don't spend on the other variant's Task 1
            }
            for (String variant : bp.bufferVariants()) {
                out.add(new Bucket(bp.type(), variant, items.countUnserved(bp.type(), variant), target));
            }
        }
        return out;
    }

    public Status status() {
        return new Status(props.buffer() != null && props.buffer().enabled(), running.get(), lastRun, lastMessage, buckets());
    }
}
