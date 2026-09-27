package com.ieltsprep.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The recurring IELTS topic pool (data/templates/topics.json) and least-practised-first rotation. */
@Component
public class TopicTaxonomy {

    public static final List<String> KEYS = List.of("education", "environment", "technology", "health", "work", "society",
            "government", "crime", "culture", "family", "media", "globalisation", "transport");

    public record Topic(String key, String label, List<String> subtopics) {}

    private final AppProperties props;
    private final TopicUsageRepository usage;
    private final Clock clock;
    private volatile Map<String, Topic> topics;

    public TopicTaxonomy(AppProperties props, TopicUsageRepository usage, Clock clock) {
        this.props = props;
        this.usage = usage;
        this.clock = clock;
    }

    public Map<String, Topic> topics() {
        Map<String, Topic> t = topics;
        if (t == null) {
            t = load();
            topics = t;
        }
        return t;
    }

    /** Topics ordered least-practised first (ties: least recently used). */
    public List<String> rotation() {
        Map<String, TopicUsage> used = new LinkedHashMap<>();
        usage.findAll().forEach(u -> used.put(u.getTopic(), u));
        List<String> keys = new ArrayList<>(KEYS);
        keys.sort(Comparator.<String>comparingInt(k -> used.containsKey(k) ? used.get(k).getTimesUsed() : 0)
                .thenComparing(k -> used.containsKey(k) && used.get(k).getLastUsedAt() != null ? used.get(k).getLastUsedAt() : Instant.EPOCH));
        return keys;
    }

    @Transactional
    public void recordUse(String topic) {
        if (topic == null) {
            return;
        }
        TopicUsage u = usage.findById(topic).orElseGet(() -> {
            TopicUsage n = new TopicUsage();
            n.setTopic(topic);
            return n;
        });
        u.setTimesUsed(u.getTimesUsed() + 1);
        u.setLastUsedAt(Instant.now(clock));
        usage.save(u);
    }

    public Map<String, Integer> coverage() {
        Map<String, Integer> out = new LinkedHashMap<>();
        KEYS.forEach(k -> out.put(k, 0));
        usage.findAll().forEach(u -> out.computeIfPresent(u.getTopic(), (k, v) -> u.getTimesUsed()));
        return out;
    }

    private Map<String, Topic> load() {
        Map<String, Topic> out = new LinkedHashMap<>();
        Path file = props.dataPath().resolve("templates/topics.json");
        if (Files.isRegularFile(file)) {
            try {
                JsonNode root = Json.MAPPER.readTree(file.toFile());
                for (JsonNode t : root.path("topics")) {
                    List<String> subs = new ArrayList<>();
                    t.path("subtopics").forEach(s -> subs.add(s.asText()));
                    out.put(t.path("key").asText(), new Topic(t.path("key").asText(), t.path("label").asText(), subs));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read " + file, e);
            }
        }
        KEYS.forEach(k -> out.putIfAbsent(k, new Topic(k, Character.toUpperCase(k.charAt(0)) + k.substring(1), List.of())));
        return out;
    }
}
