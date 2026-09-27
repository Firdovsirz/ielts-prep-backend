package com.ieltsprep.claude;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JSON schemas for structured outputs, loaded from {@code prompts/schemas/<name>.schema.json}. Each schema maps to
 * a Java record that the response is deserialised into.
 */
@Component
public class SchemaRepository {

    private static final Logger log = LoggerFactory.getLogger(SchemaRepository.class);

    private final Path dir;
    private final Map<String, Map<String, Object>> schemas = new TreeMap<>();

    public SchemaRepository(AppProperties props) {
        this.dir = props.promptsPath().resolve("schemas");
    }

    @PostConstruct
    public synchronized void load() {
        schemas.clear();
        if (!Files.isDirectory(dir)) {
            log.warn("Schema directory {} not found", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".schema.json")).forEach(p -> {
                String name = p.getFileName().toString().replace(".schema.json", "");
                try {
                    schemas.put(name, forApi(Json.MAPPER.readTree(p.toFile())));
                } catch (IOException e) {
                    throw new UncheckedIOException("Bad schema " + p, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Loaded {} output schemas", schemas.size());
    }

    public Map<String, Object> get(String name) {
        Map<String, Object> schema = schemas.get(name);
        if (schema == null) {
            throw new IllegalArgumentException("Unknown schema '" + name + "' in " + dir);
        }
        return schema;
    }

    public boolean has(String name) {
        return schemas.containsKey(name);
    }

    /** Drops documentation-only keywords ($schema, title) the API does not need. */
    static Map<String, Object> forApi(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>(Json.MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {}));
        map.remove("$schema");
        map.remove("title");
        return map;
    }
}
