package com.ieltsprep.claude;

import com.ieltsprep.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/** Loads every {@code prompts/*.md} file at startup. Prompts are files so they can be tuned without recompiling. */
@Component
public class PromptRepository {

    private static final Logger log = LoggerFactory.getLogger(PromptRepository.class);

    private final Path dir;
    private final Map<String, PromptTemplate> prompts = new TreeMap<>();

    public PromptRepository(AppProperties props) {
        this.dir = props.promptsPath();
    }

    @PostConstruct
    public synchronized void load() {
        prompts.clear();
        if (!Files.isDirectory(dir)) {
            log.warn("Prompts directory {} not found — Claude features will be unavailable", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("README.md"))
                    .forEach(p -> {
                        PromptTemplate t = parse(p);
                        prompts.put(t.name(), t);
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Loaded {} prompts from {}", prompts.size(), dir);
    }

    public PromptTemplate get(String name) {
        PromptTemplate t = prompts.get(name);
        if (t == null) {
            throw new IllegalArgumentException("Unknown prompt '" + name + "' (expected " + dir.resolve(name + ".md") + ")");
        }
        return t;
    }

    public Map<String, PromptTemplate> all() {
        return Map.copyOf(prompts);
    }

    static PromptTemplate parse(Path file) {
        String fileName = file.getFileName().toString();
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parse(fileName.substring(0, fileName.length() - 3), text);
    }

    @SuppressWarnings("unchecked")
    static PromptTemplate parse(String defaultName, String text) {
        Map<String, Object> meta = Map.of();
        String body = text;
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---\n", 4);
            if (end < 0) {
                throw new IllegalStateException("Prompt " + defaultName + ": unterminated front matter");
            }
            Object parsed = new Yaml().load(text.substring(4, end));
            meta = parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            body = text.substring(end + 5);
        }
        String system = section(body, "# System");
        String user = section(body, "# User");
        if (system == null || user == null) {
            throw new IllegalStateException("Prompt " + defaultName + " needs '# System' and '# User' sections");
        }
        String name = String.valueOf(meta.getOrDefault("name", defaultName));
        ModelRoute route = ModelRoute.valueOf(String.valueOf(meta.getOrDefault("route", "GENERATION")).toUpperCase(Locale.ROOT));
        Object schema = meta.get("schema");
        Object context = meta.get("context");
        List<String> contextFiles = context instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        Object maxTokens = meta.get("max_tokens");
        return new PromptTemplate(
                name,
                String.valueOf(meta.getOrDefault("description", "")),
                route,
                schema == null ? null : String.valueOf(schema),
                contextFiles,
                maxTokens == null ? null : Long.valueOf(String.valueOf(maxTokens)),
                system,
                user);
    }

    /** Text between {@code heading} and the next {@code # System}/{@code # User} line (other headings are content). */
    private static String section(String body, String heading) {
        List<String> lines = body.lines().toList();
        int start = -1;
        for (int i = 0; i < lines.size() && start < 0; i++) {
            if (lines.get(i).trim().equals(heading)) {
                start = i + 1;
            }
        }
        if (start < 0) {
            return null;
        }
        int end = lines.size();
        for (int i = start; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equals("# System") || t.equals("# User")) {
                end = i;
                break;
            }
        }
        return String.join("\n", lines.subList(start, end)).trim();
    }
}
