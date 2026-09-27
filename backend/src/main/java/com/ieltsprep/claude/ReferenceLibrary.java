package com.ieltsprep.claude;

import com.ieltsprep.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Static reference documents under {@code data/} (band descriptors, format templates, question-type specs)
 * that prompts list in their {@code context:} front matter. They form the cached system-prompt prefix.
 */
@Component
public class ReferenceLibrary {

    private final Path dataDir;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ReferenceLibrary(AppProperties props) {
        this.dataDir = props.dataPath();
    }

    public String text(String relativePath) {
        return cache.computeIfAbsent(relativePath, this::read);
    }

    public boolean exists(String relativePath) {
        return Files.isRegularFile(dataDir.resolve(relativePath).normalize());
    }

    public void invalidate() {
        cache.clear();
    }

    private String read(String relativePath) {
        Path file = dataDir.resolve(relativePath).normalize();
        if (!file.startsWith(dataDir)) {
            throw new IllegalArgumentException("Reference outside data dir: " + relativePath);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Missing reference file data/" + relativePath
                    + " — run the backend once (it copies defaults) or `--task=fetch-templates`.", e);
        }
    }
}
