package com.ieltsprep.system;

import com.ieltsprep.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes all practice history (sessions, attempts, error log, grammar, vocabulary, plan, coach reports, mock tests
 * and speaking recordings). Content items, settings, the admin account and the API cost log are kept; item serve
 * counters are reset so every passage and task can come round again.
 */
@Service
public class ProgressResetService {

    private static final Logger log = LoggerFactory.getLogger(ProgressResetService.class);

    /** Child tables first. */
    static final List<String> TABLES = List.of("speaking_responses", "errors", "attempts", "vocab_reviews", "vocab_cards", "grammar_progress",
            "grammar_diagnostics", "study_plan", "plan_meta", "coach_reports", "mock_tests", "sessions", "topic_usage");

    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public ProgressResetService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional
    public Map<String, Integer> reset() {
        Map<String, Integer> deleted = new LinkedHashMap<>();
        for (String table : TABLES) {
            deleted.put(table, jdbc.update("delete from " + table));
        }
        jdbc.update("update items set times_served = 0, last_served_at = null");
        deleteRecordings(props.recordingsPath());
        log.warn("Progress reset: {}", deleted);
        return deleted;
    }

    private static void deleteRecordings(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(dir) && !p.getFileName().toString().equals(".gitkeep")).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clear recordings in {}", dir);
        }
    }
}
