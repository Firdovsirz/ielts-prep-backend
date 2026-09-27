package com.ieltsprep.system;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Creates the data directory layout and, when IELTS_DEFAULTS_DIR is set (Docker image), copies the bundled
 * templates/descriptors/sources/seed files into an empty data volume. Existing files are never overwritten.
 * <p>
 * Runs as soon as the configuration is loaded — before any bean is created — because several beans (the grammar
 * taxonomy, the AWL list, the seed loader) read these files while the context starts. Registered in
 * META-INF/spring.factories.
 */
public class DataDirInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DataDirInitializer.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        Path data = Path.of(env.getProperty("ielts.data-dir", "./../data")).toAbsolutePath().normalize();
        initialise(data, env.getProperty("ielts.defaults-dir", ""));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // after the config files have been loaded into the environment
    }

    public static void initialise(Path data, String defaults) {
        try {
            for (String dir : new String[] {"db", "audio", "recordings", "templates", "descriptors", "sources", "seed"}) {
                Files.createDirectories(data.resolve(dir));
            }
            if (defaults == null || defaults.isBlank()) {
                return;
            }
            Path from = Path.of(defaults).toAbsolutePath().normalize();
            if (!Files.isDirectory(from) || from.equals(data)) {
                return;
            }
            int copied = 0;
            try (Stream<Path> walk = Files.walk(from)) {
                for (Path src : walk.filter(Files::isRegularFile).toList()) {
                    Path target = data.resolve(from.relativize(src).toString());
                    if (!Files.exists(target)) {
                        Files.createDirectories(target.getParent());
                        Files.copy(src, target, StandardCopyOption.COPY_ATTRIBUTES);
                        copied++;
                    }
                }
            }
            if (copied > 0) {
                log.info("Copied {} default data files from {} to {}", copied, from, data);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot initialise data directory " + data, e);
        }
    }
}
