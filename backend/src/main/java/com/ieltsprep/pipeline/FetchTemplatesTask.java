package com.ieltsprep.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * --task=fetch-templates: fetches the official free IELTS format pages, sample tasks and band descriptors listed in
 * data/sources/official-samples.json (robots.txt respected, rate-limited). Band descriptor text is stored locally in
 * data/descriptors/official/ (gitignored). With an API key, Claude extracts the FORMAT structure of each document into
 * data/templates/official/&lt;id&gt;.json for review against the curated templates. Test content is never stored.
 */
@Component
public class FetchTemplatesTask {

    private final AppProperties props;
    private final PoliteFetcher fetcher;
    private final ClaudeService claude;

    public FetchTemplatesTask(AppProperties props, PoliteFetcher fetcher, ClaudeService claude) {
        this.props = props;
        this.fetcher = fetcher;
        this.claude = claude;
    }

    public int run(Consumer<String> out) throws Exception {
        Path list = props.dataPath().resolve("sources/official-samples.json");
        Path descriptors = props.dataPath().resolve("descriptors/official");
        Path templates = props.dataPath().resolve("templates/official");
        Files.createDirectories(descriptors);
        Files.createDirectories(templates);
        if (!claude.isAvailable()) {
            out.accept("No API key: band descriptors will be saved, format extraction skipped.");
        }
        int ok = 0;
        for (JsonNode doc : Json.MAPPER.readTree(list.toFile()).path("documents")) {
            String id = doc.path("id").asText();
            String kind = doc.path("kind").asText();
            var fetched = fetcher.fetch(doc.path("url").asText());
            if (fetched.isEmpty()) {
                out.accept("  ✗ " + id);
                continue;
            }
            String text = TextExtractor.text(fetched.get());
            if ("BAND_DESCRIPTORS".equals(kind)) {
                Files.writeString(descriptors.resolve(id + ".txt"), "Source: " + doc.path("url").asText() + "\n\n" + text);
            }
            if (claude.isAvailable()) {
                JsonNode structure = claude.call(ClaudeCall.of("templates-extract", Map.of(
                        "doc_id", id, "kind", kind, "module", doc.path("module").asText(), "url", doc.path("url").asText(),
                        "text", text.length() > 60_000 ? text.substring(0, 60_000) : text), JsonNode.class).ref("fetch-templates " + id));
                Files.writeString(templates.resolve(id + ".json"), Json.pretty(structure));
            }
            ok++;
            out.accept("  ✓ " + id + " (" + text.length() + " chars" + ("BAND_DESCRIPTORS".equals(kind) ? ", descriptor text saved" : "") + ")");
        }
        out.accept(ok + " official documents processed. Review data/templates/official/ against the curated data/templates/*.json.");
        return 0;
    }
}
