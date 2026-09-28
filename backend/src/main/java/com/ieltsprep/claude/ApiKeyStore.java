package com.ieltsprep.claude;

import com.ieltsprep.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The Anthropic API key in effect. A key entered in the app (Settings → Claude API) is saved to
 * {@code <data-dir>/.anthropic-api-key} (owner-only permissions) and takes precedence over ANTHROPIC_API_KEY from the
 * environment/.env, so the key can be added or replaced without editing files or restarting. The key is never
 * returned by the API or logged — only its last four characters.
 */
@Component
public class ApiKeyStore {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStore.class);
    static final String FILE = ".anthropic-api-key";

    public enum Source { APP, ENV, NONE }

    private final String envKey;
    private final Path file;
    private final AtomicLong version = new AtomicLong();
    private volatile String savedKey;

    @Autowired
    public ApiKeyStore(ClaudeProperties props, AppProperties app, @Value("${claude.api-key-file:}") String keyFile) {
        this(props.apiKey(), keyFile == null || keyFile.isBlank() ? app.dataPath().resolve(FILE) : Path.of(keyFile).toAbsolutePath());
    }

    ApiKeyStore(String envKey, Path file) {
        this.envKey = envKey == null || envKey.isBlank() ? null : envKey.trim();
        this.file = file;
        try {
            this.savedKey = Files.isRegularFile(file) ? blankToNull(Files.readString(file).trim()) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    public Optional<String> current() {
        String saved = savedKey;
        return Optional.ofNullable(saved != null ? saved : envKey);
    }

    public boolean present() {
        return current().isPresent();
    }

    public Source source() {
        return savedKey != null ? Source.APP : envKey != null ? Source.ENV : Source.NONE;
    }

    /** "…Ab12" for display, or null. */
    public String hint() {
        return current().map(k -> "…" + k.substring(Math.max(0, k.length() - 4))).orElse(null);
    }

    /** Changes whenever the key does, so the SDK client can be rebuilt. */
    public long version() {
        return version.get();
    }

    public synchronized void save(String key) {
        String k = key.trim();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, k + "\n");
            ownerOnly(tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save the API key to " + file, e);
        }
        savedKey = k;
        version.incrementAndGet();
        log.info("Anthropic API key saved in the app (ending {})", hint());
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + file, e);
        }
        savedKey = null;
        version.incrementAndGet();
        log.info("Saved Anthropic API key removed; {}", envKey == null ? "no key configured" : "using ANTHROPIC_API_KEY from the environment");
    }

    private static void ownerOnly(Path p) {
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // not a POSIX file system (Windows): rely on the directory's permissions
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
