package com.ieltsprep.grammar;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The 13 grammar areas and the error-subtype vocabulary (data/descriptors/grammar-areas.json) that graders must use
 * when tagging errors. Every grammar subtype maps to exactly one area.
 */
@Component
public class GrammarTaxonomy {

    public record Area(String key, String name, String focus, List<String> subtypes) {}

    private final Map<String, Area> areas = new LinkedHashMap<>();
    private final Map<String, String> subtypeToArea = new LinkedHashMap<>();
    private final Map<String, List<String>> otherSubtypes = new LinkedHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public GrammarTaxonomy(AppProperties props) {
        this(props.dataPath().resolve("descriptors/grammar-areas.json"));
    }

    GrammarTaxonomy(Path file) {
        try {
            JsonNode root = Json.MAPPER.readTree(file.toFile());
            for (JsonNode a : root.path("areas")) {
                List<String> subs = new ArrayList<>();
                a.path("subtypes").forEach(s -> subs.add(s.asText()));
                Area area = new Area(a.path("key").asText(), a.path("name").asText(), a.path("focus").asText(), subs);
                areas.put(area.key(), area);
                subs.forEach(s -> subtypeToArea.put(s, area.key()));
            }
            root.path("non_grammar_subtypes").fields().forEachRemaining(e -> {
                List<String> subs = new ArrayList<>();
                e.getValue().forEach(s -> subs.add(s.asText()));
                otherSubtypes.put(e.getKey(), subs);
            });
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read grammar taxonomy " + file, e);
        }
    }

    public List<Area> areas() {
        return List.copyOf(areas.values());
    }

    public Optional<Area> area(String key) {
        return Optional.ofNullable(areas.get(key));
    }

    public Area require(String key) {
        return area(key).orElseThrow(() -> new IllegalArgumentException("Unknown grammar area " + key));
    }

    /** Grammar area for a (normalised) grammar subtype, if known. */
    public Optional<String> areaForSubtype(String subtype) {
        return Optional.ofNullable(subtypeToArea.get(normalise(subtype)));
    }

    public boolean isKnownSubtype(String type, String subtype) {
        String s = normalise(subtype);
        if ("grammar".equalsIgnoreCase(type)) {
            return subtypeToArea.containsKey(s);
        }
        return otherSubtypes.getOrDefault(type.toLowerCase(Locale.ROOT), List.of()).contains(s);
    }

    public List<String> subtypes(String type) {
        if ("grammar".equalsIgnoreCase(type)) {
            return List.copyOf(subtypeToArea.keySet());
        }
        return otherSubtypes.getOrDefault(type.toLowerCase(Locale.ROOT), List.of());
    }

    public static String normalise(String subtype) {
        return subtype == null ? "" : subtype.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }
}
