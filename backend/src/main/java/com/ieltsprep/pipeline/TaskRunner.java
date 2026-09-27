package com.ieltsprep.pipeline;

import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.SeedLoader;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.generation.BatchGenerationService;
import com.ieltsprep.generation.ContentBuffer;
import com.ieltsprep.generation.GenerationRequest;
import com.ieltsprep.generation.GenerationService;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Content-pipeline CLI. Run from backend/:
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=fetch-templates"
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=fetch-sources [--refresh]"
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=generate --module=reading --count=10 [--variant=P3] [--sync] [--no-wait]"
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=buffer"          (top up every bucket now)
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=seed [--update]"
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=status"
 * </pre>
 */
@Component
@Order(100)
public class TaskRunner implements ApplicationRunner, ExitCodeGenerator {

    static final Map<String, List<TaskType>> MODULES = Map.of(
            "reading", List.of(TaskType.READING_PASSAGE),
            "listening", List.of(TaskType.LISTENING_SECTION),
            "writing", List.of(TaskType.WRITING_TASK1_ACADEMIC, TaskType.WRITING_TASK2),
            "writing-general", List.of(TaskType.WRITING_TASK1_GENERAL),
            "speaking", List.of(TaskType.SPEAKING_PART1, TaskType.SPEAKING_PART2, TaskType.SPEAKING_PART3),
            "grammar", List.of(TaskType.GRAMMAR_EXERCISE),
            "vocab", List.of(TaskType.VOCAB_WORD_BANK));

    private final FetchTemplatesTask fetchTemplates;
    private final FetchSourcesTask fetchSources;
    private final GenerationService generation;
    private final BatchGenerationService batches;
    private final ContentBuffer buffer;
    private final SeedLoader seeds;
    private final ItemRepository items;
    private int exitCode;

    public TaskRunner(FetchTemplatesTask fetchTemplates, FetchSourcesTask fetchSources, GenerationService generation,
            BatchGenerationService batches, ContentBuffer buffer, SeedLoader seeds, ItemRepository items) {
        this.fetchTemplates = fetchTemplates;
        this.fetchSources = fetchSources;
        this.generation = generation;
        this.batches = batches;
        this.buffer = buffer;
        this.seeds = seeds;
        this.items = items;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("task")) {
            return;
        }
        String task = first(args, "task", "");
        try {
            exitCode = switch (task) {
                case "fetch-templates" -> fetchTemplates.run(TaskRunner::print);
                case "fetch-sources" -> fetchSources.run(args.containsOption("refresh"), TaskRunner::print);
                case "generate" -> generate(args);
                case "buffer" -> {
                    print("Topping up the content buffer…");
                    print("Generated " + buffer.topUp(Integer.parseInt(first(args, "max", "50"))) + " items");
                    yield 0;
                }
                case "seed" -> {
                    SeedLoader.Summary s = seeds.load(args.containsOption("update"));
                    print("Seed: " + s.inserted() + " inserted, " + s.updated() + " updated, " + s.skipped() + " unchanged");
                    s.problems().forEach(p -> print("  problem: " + p));
                    yield s.problems().isEmpty() ? 0 : 1;
                }
                case "status" -> {
                    items.inventory().forEach(r -> print(String.format("%-10s %-24s %-9s %d", r[0], r[1], r[2], r[3])));
                    buffer.buckets().forEach(b -> print("buffer " + b.type() + (b.variant() == null ? "" : " " + b.variant())
                            + ": " + b.unserved() + "/" + b.target() + " unseen"));
                    yield 0;
                }
                default -> {
                    print("Unknown task '" + task + "'. Tasks: fetch-templates, fetch-sources, generate, buffer, seed, status");
                    yield 2;
                }
            };
        } catch (Exception e) {
            print("Task failed: " + e.getMessage());
            exitCode = 1;
        }
    }

    private int generate(ApplicationArguments args) throws Exception {
        String module = first(args, "module", "reading").toLowerCase(Locale.ROOT);
        List<TaskType> types = args.containsOption("task-type")
                ? List.of(TaskType.valueOf(first(args, "task-type", "").toUpperCase(Locale.ROOT)))
                : MODULES.get(module);
        if (types == null) {
            print("Unknown module '" + module + "'. Modules: " + MODULES.keySet());
            return 2;
        }
        int count = Integer.parseInt(first(args, "count", "5"));
        String variant = args.containsOption("variant") ? first(args, "variant", null) : null;
        if (args.containsOption("sync")) {
            int verified = 0;
            for (int i = 0; i < count; i++) {
                TaskType type = types.get(i % types.size());
                GenerationService.Outcome o = generation.generate(GenerationRequest.of(type, variant, true));
                print((o.verified() ? "  ✓ " : "  ✗ ") + type + " → " + (o.item() == null ? "-" : o.item().getTitle()) + " (" + o.attempts() + " attempt(s))");
                verified += o.verified() ? 1 : 0;
            }
            print(verified + "/" + count + " verified");
            return 0;
        }
        for (TaskType type : types) {
            int n = (int) Math.ceil(count / (double) types.size());
            var submitted = batches.submitGeneration(type, variant, n);
            print("Submitted batch " + submitted.batchId() + " with " + submitted.requests() + " × " + type);
        }
        if (args.containsOption("no-wait")) {
            print("Not waiting — a running backend polls and verifies the batch automatically.");
            return 0;
        }
        print("Waiting for batch results (generate → blind verify)… Ctrl+C is safe; a running backend continues the job.");
        batches.awaitAll(Duration.ofSeconds(30), TaskRunner::print);
        print("Done.");
        return 0;
    }

    private static String first(ApplicationArguments args, String name, String fallback) {
        List<String> v = args.getOptionValues(name);
        return v == null || v.isEmpty() ? fallback : v.getFirst();
    }

    private static void print(String s) {
        System.out.println(s);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
