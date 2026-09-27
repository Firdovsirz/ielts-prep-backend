package com.ieltsprep.vocab;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Academic Word List sublist for a word (data/descriptors/awl.json, Coxhead 2000). Headwords match exactly; other
 * members of a word family match by a shared stem of at least five letters (analyse → analysis, economy → economic).
 */
@Component
public class AwlLookup {

    private final Map<String, Integer> headwords = new HashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AwlLookup(AppProperties props) {
        this(props.dataPath().resolve("descriptors/awl.json"));
    }

    AwlLookup(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonNode root = Json.MAPPER.readTree(file.toFile());
            root.path("sublists").fields().forEachRemaining(e -> e.getValue().forEach(w -> headwords.put(w.asText(), Integer.valueOf(e.getKey()))));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    public Optional<Integer> sublist(String word) {
        if (word == null || word.contains(" ")) {
            return Optional.empty();
        }
        String w = word.toLowerCase(Locale.ROOT).trim();
        Integer exact = headwords.get(w);
        if (exact == null && w.endsWith("s")) {
            exact = headwords.get(w.replaceAll("(es|s)$", ""));
            if (exact == null) {
                exact = headwords.get(w.substring(0, w.length() - 1));
            }
        }
        if (exact != null) {
            return Optional.of(exact);
        }
        return headwords.entrySet().stream()
                .filter(e -> {
                    String stem = stem(e.getKey());
                    return stem.length() >= 5 && w.startsWith(stem) && w.length() - stem.length() <= 6;
                })
                .map(Map.Entry::getValue).min(Integer::compare);
    }

    public int size() {
        return headwords.size();
    }

    static String stem(String headword) {
        return headword.replaceAll("(e|y|ies|ise|ize)$", "");
    }
}
