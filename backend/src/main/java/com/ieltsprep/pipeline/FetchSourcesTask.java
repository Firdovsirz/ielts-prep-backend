package com.ieltsprep.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.config.AppProperties;
import com.ieltsprep.generation.SourceSeedService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * --task=fetch-sources: downloads open-licence text for every topic seed (data/sources/seeds.json) into
 * data/sources/cache/&lt;id&gt;.json (URL, licence, fetch date, plain-text extract) and collects RSS headlines as topic ideas
 * (data/sources/cache/rss-topics.json). Extracts are only ever given to Claude as factual grounding.
 */
@Component
public class FetchSourcesTask {

    private static final int MAX_CHARS = 9000;

    private final AppProperties props;
    private final PoliteFetcher fetcher;
    private final SourceSeedService seeds;

    public FetchSourcesTask(AppProperties props, PoliteFetcher fetcher, SourceSeedService seeds) {
        this.props = props;
        this.fetcher = fetcher;
        this.seeds = seeds;
    }

    public int run(boolean refresh, Consumer<String> out) throws Exception {
        Path cache = props.dataPath().resolve("sources/cache");
        Files.createDirectories(cache);
        int fetched = 0;
        int skipped = 0;
        for (SourceSeedService.Seed seed : seeds.all()) {
            Path file = cache.resolve(seed.id() + ".json");
            if (Files.exists(file) && !refresh) {
                skipped++;
                continue;
            }
            var page = fetcher.fetch(seed.url());
            if (page.isEmpty()) {
                out.accept("  ✗ " + seed.id() + " (not fetched)");
                continue;
            }
            String text = TextExtractor.text(page.get());
            ObjectNode doc = Json.MAPPER.createObjectNode();
            doc.put("id", seed.id());
            doc.put("url", seed.url());
            doc.put("licence", seed.licence());
            doc.put("provider", seed.provider());
            doc.put("fetched_at", Instant.now().toString());
            doc.put("extract", text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text);
            Files.writeString(file, Json.pretty(doc));
            fetched++;
            out.accept("  ✓ " + seed.id() + " (" + Math.min(text.length(), MAX_CHARS) + " chars)");
        }
        out.accept("Seeds: " + fetched + " fetched, " + skipped + " already cached");
        fetchRss(cache, out);
        seeds.reload();
        return 0;
    }

    private void fetchRss(Path cache, Consumer<String> out) throws Exception {
        Path feedsFile = props.dataPath().resolve("sources/rss-feeds.json");
        if (!Files.exists(feedsFile)) {
            return;
        }
        ArrayNode topics = Json.MAPPER.createArrayNode();
        for (JsonNode feed : Json.MAPPER.readTree(feedsFile.toFile()).path("feeds")) {
            var res = fetcher.fetch(feed.path("url").asText());
            if (res.isEmpty()) {
                continue;
            }
            Document xml = Jsoup.parse(res.get().text(), "", Parser.xmlParser());
            int n = 0;
            for (Element entry : xml.select("item, entry")) {
                String title = entry.selectFirst("title") == null ? "" : entry.selectFirst("title").text();
                Element linkEl = entry.selectFirst("link");
                String link = linkEl == null ? "" : linkEl.hasAttr("href") ? linkEl.attr("href") : linkEl.text();
                if (title.isBlank()) {
                    continue;
                }
                ObjectNode t = topics.addObject();
                t.put("feed", feed.path("id").asText());
                t.put("title", title);
                t.put("link", link);
                t.put("licence", "headline used only as a topic idea");
                if (++n >= 25) {
                    break;
                }
            }
            out.accept("  RSS " + feed.path("id").asText() + ": " + n + " headlines");
        }
        ObjectNode doc = Json.MAPPER.createObjectNode();
        doc.put("fetched_at", Instant.now().toString());
        doc.set("topics", topics);
        Files.writeString(cache.resolve("rss-topics.json"), Json.pretty(doc));
    }
}
