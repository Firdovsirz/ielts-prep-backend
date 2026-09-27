package com.ieltsprep.system;

import com.ieltsprep.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the data directory layout and, when IELTS_DEFAULTS_DIR is set (Docker image), copies the bundled
 * templates/descriptors/sources/seed files into an empty data volume. Existing files are never overwritten.
 */
@Component
@Order(0)
public class DataDirInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataDirInitializer.class);

    private final AppProperties props;

    public DataDirInitializer(AppProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path data = props.dataPath();
        try {
            for (String dir : new String[] {"db", "audio", "recordings", "templates", "descriptors", "sources", "seed"}) {
                Files.createDirectories(data.resolve(dir));
            }
            String defaults = props.defaultsDir();
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
