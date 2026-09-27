package com.ieltsprep.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieltsprep.common.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Dumps the full study record (every session, attempt, feedback, error, card, plan and API call) as JSON or CSV. */
@Service
public class ExportService {

    /** Tables exported, in dependency order. app_users (password hashes) is deliberately excluded. */
    static final List<String> TABLES = List.of("user_settings", "sessions", "attempts", "errors", "grammar_progress",
            "grammar_diagnostics", "vocab_cards", "vocab_reviews", "speaking_responses", "study_plan", "coach_reports", "mock_tests",
            "topic_usage", "api_usage", "batch_jobs");

    /** Items are exported without their (large) content, as a catalogue of what was practised. */
    static final String ITEMS_QUERY = "select id, module, task_type, variant, question_types, exam_type, difficulty, cefr, topic, title, "
            + "source_url, licence, verification_status, origin, times_served, created_at from items order by id";

    static final Set<String> JSON_COLUMNS = Set.of("my_answers", "per_criterion_bands", "per_question", "feedback", "state", "report",
            "stats", "examples", "collocations", "details", "item_ids_json");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ExportService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public byte[] json() {
        ObjectNode root = Json.MAPPER.createObjectNode();
        root.put("exported_at", Instant.now(clock).toString());
        root.put("format", "ielts-prep export v1");
        for (Map.Entry<String, List<Map<String, Object>>> e : tables().entrySet()) {
            var array = root.putArray(e.getKey());
            for (Map<String, Object> row : e.getValue()) {
                ObjectNode node = array.addObject();
                row.forEach((k, v) -> {
                    String key = k.toLowerCase();
                    if (v instanceof String s && JSON_COLUMNS.contains(key) && looksLikeJson(s)) {
                        node.set(key, parse(s));
                    } else {
                        node.set(key, Json.MAPPER.valueToTree(v));
                    }
                });
            }
        }
        try {
            return Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ZIP with one CSV per table (JSON columns kept as JSON text). */
    public byte[] csvZip() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, List<Map<String, Object>>> e : tables().entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey() + ".csv"));
                zip.write(csv(e.getValue()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    Map<String, List<Map<String, Object>>> tables() {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (String t : TABLES) {
            out.put(t, jdbc.queryForList("select * from " + t + " order by 1"));
        }
        out.put("items", jdbc.queryForList(ITEMS_QUERY));
        return out;
    }

    static String csv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<String> cols = List.copyOf(rows.getFirst().keySet());
        sb.append(String.join(",", cols.stream().map(c -> escape(c.toLowerCase())).toList())).append("\r\n");
        for (Map<String, Object> row : rows) {
            sb.append(String.join(",", cols.stream().map(c -> escape(row.get(c) == null ? "" : String.valueOf(row.get(c)))).toList()))
                    .append("\r\n");
        }
        return sb.toString();
    }

    static String escape(String v) {
        boolean quote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        return quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static boolean looksLikeJson(String s) {
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private static JsonNode parse(String s) {
        try {
            return Json.MAPPER.readTree(s);
        } catch (IOException e) {
            return Json.MAPPER.getNodeFactory().textNode(s);
        }
    }
}
