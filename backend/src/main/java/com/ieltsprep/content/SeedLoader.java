package com.ieltsprep.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads {@code data/seed/**.json} into the items table on startup (insert-only; {@code --task=seed --update}
 * refreshes changed seeds). Seed files use the same JSON shape Claude generates, pass the same validator, and are
 * stored as VERIFIED so every module is usable before any API call.
 */
@Component
@Order(10)
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    public record Summary(int inserted, int updated, int skipped, List<String> problems) {}

    private final ItemRepository items;
    private final ItemFactory factory;
    private final ItemValidator validator;
    private final AppProperties props;

    public SeedLoader(ItemRepository items, ItemFactory factory, ItemValidator validator, AppProperties props) {
        this.items = items;
        this.factory = factory;
        this.validator = validator;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedOnStartup() || args.containsOption("task")) {
            return;
        }
        Summary s = load(false);
        if (s.inserted() > 0 || !s.problems().isEmpty()) {
            log.info("Seed content: {} inserted, {} already present, {} problems", s.inserted(), s.skipped(), s.problems().size());
        }
        s.problems().forEach(p -> log.warn("Seed problem: {}", p));
    }

    @Transactional
    public Summary load(boolean update) {
        Path root = props.dataPath().resolve("seed");
        if (!Files.isDirectory(root)) {
            return new Summary(0, 0, 0, List.of("No seed directory at " + root));
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            JsonNode doc;
            try {
                doc = Json.MAPPER.readTree(file.toFile());
            } catch (IOException e) {
                problems.add(rel + ": " + e.getMessage());
                continue;
            }
            List<JsonNode> entries = new ArrayList<>();
            if (doc.isArray()) {
                doc.forEach(entries::add);
            } else {
                entries.add(doc);
            }
            for (int i = 0; i < entries.size(); i++) {
                JsonNode entry = entries.get(i);
                String key = entries.size() == 1 ? rel : rel + "#" + entry.path("content").path("id").asText(String.valueOf(i));
                try {
                    TaskType type = TaskType.valueOf(entry.path("task_type").asText());
                    Object content = Json.MAPPER.treeToValue(entry.path("content"), type.contentType());
                    ItemValidator.Report report = validator.validate(type, content);
                    if (!report.ok()) {
                        problems.add(key + ": " + report.summary());
                        continue;
                    }
                    var existing = items.findBySeedKey(key);
                    if (existing.isPresent()) {
                        if (update) {
                            Item item = existing.get();
                            factory.apply(item, content);
                            item.setSourceUrl(emptyToNull(entry.path("source_url").asText()));
                            item.setLicence(emptyToNull(entry.path("licence").asText()));
                            items.save(item);
                            updated++;
                        } else {
                            skipped++;
                        }
                        continue;
                    }
                    Item item = factory.create(type, content, entry.path("source_url").asText(), entry.path("licence").asText(), ItemOrigin.SEED);
                    item.setSeedKey(key);
                    item.setVerificationStatus(VerificationStatus.VERIFIED);
                    item.setVerificationNotes("Seed content: validated by ItemValidator and a blind human-style answer check at authoring time.");
                    items.save(item);
                    inserted++;
                } catch (Exception e) {
                    problems.add(key + ": " + e.getMessage());
                }
            }
        }
        return new Summary(inserted, updated, skipped, problems);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
