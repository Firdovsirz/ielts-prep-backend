package com.ieltsprep.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import com.ieltsprep.content.ItemRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/**
 * Open-licence topic seeds (data/sources/seeds.json) for Reading passages and Listening scripts. A seed is only a
 * topic prompt plus optional factual grounding from the fetched cache — Claude always writes original text.
 */
@Service
public class SourceSeedService {

    public record Seed(String id, String title, String url, String licence, String provider, String topic,
            List<String> modules, String summary) {}

    private final AppProperties props;
    private final ItemRepository items;
    private volatile List<Seed> seeds;

    public SourceSeedService(AppProperties props, ItemRepository items) {
        this.props = props;
        this.items = items;
    }

    public List<Seed> all() {
        List<Seed> s = seeds;
        if (s == null) {
            s = load();
            seeds = s;
        }
        return s;
    }

    public void reload() {
        seeds = null;
    }

    /** Least-used seed for a module (by how many items already cite its URL), random among ties. */
    public Optional<Seed> pick(String module, List<String> preferTopics) {
        List<Seed> candidates = all().stream().filter(s -> s.modules().contains(module)).toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<Seed> shuffled = new ArrayList<>(candidates);
        java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return shuffled.stream().min(Comparator
                .comparingLong((Seed s) -> items.countBySourceUrl(s.url()))
                .thenComparingInt(s -> preferTopics == null || preferTopics.isEmpty() ? 0
                        : preferTopics.contains(s.topic()) ? preferTopics.indexOf(s.topic()) : preferTopics.size()));
    }

    /** Cached plain-text extract from `--task=fetch-sources`, trimmed for use as background facts. */
    public String extract(Seed seed, int maxChars) {
        Path file = props.dataPath().resolve("sources/cache").resolve(seed.id() + ".json");
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            String text = Json.MAPPER.readTree(file.toFile()).path("extract").asText("");
            return text.length() <= maxChars ? text : text.substring(0, maxChars) + " …";
        } catch (Exception e) {
            return "";
        }
    }

    private List<Seed> load() {
        Path file = props.dataPath().resolve("sources/seeds.json");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<Seed> out = new ArrayList<>();
            for (JsonNode n : Json.MAPPER.readTree(file.toFile()).path("seeds")) {
                List<String> modules = new ArrayList<>();
                n.path("modules").forEach(m -> modules.add(m.asText()));
                out.add(new Seed(n.path("id").asText(), n.path("title").asText(), n.path("url").asText(),
                        n.path("licence").asText(), n.path("provider").asText(), n.path("topic").asText(), modules,
                        n.path("summary").asText()));
            }
            return List.copyOf(out);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
