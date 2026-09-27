package com.ieltsprep.pipeline;

import com.ieltsprep.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** HTTP fetcher for the content pipeline: honours robots.txt, rate-limits per host and identifies itself. */
@Component
public class PoliteFetcher {

    private static final Logger log = LoggerFactory.getLogger(PoliteFetcher.class);

    public record Fetched(String url, int status, String contentType, byte[] body) {
        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public boolean isPdf() {
            return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf") || url.toLowerCase(Locale.ROOT).endsWith(".pdf");
        }
    }

    /** Disallow/Allow rules for our user agent (or "*"), longest match wins. */
    record Robots(List<String> allow, List<String> disallow) {
        boolean allowed(String path) {
            int bestAllow = allow.stream().filter(path::startsWith).mapToInt(String::length).max().orElse(-1);
            int bestDisallow = disallow.stream().filter(p -> !p.isEmpty() && path.startsWith(p)).mapToInt(String::length).max().orElse(-1);
            return bestAllow >= bestDisallow;
        }

        static Robots parse(String text, String agentToken) {
            List<String> allow = new ArrayList<>();
            List<String> disallow = new ArrayList<>();
            List<String> specificAllow = new ArrayList<>();
            List<String> specificDisallow = new ArrayList<>();
            boolean inStar = false;
            boolean inOurs = false;
            boolean lastWasAgent = false;
            for (String raw : text.split("\\R")) {
                String line = raw.replaceAll("#.*", "").trim();
                if (line.isEmpty()) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (key.equals("user-agent")) {
                    if (!lastWasAgent) {
                        inStar = false;
                        inOurs = false;
                    }
                    inStar |= value.equals("*");
                    inOurs |= value.toLowerCase(Locale.ROOT).contains(agentToken);
                    lastWasAgent = true;
                    continue;
                }
                lastWasAgent = false;
                if (key.equals("allow") || key.equals("disallow")) {
                    List<String> target = inOurs ? (key.equals("allow") ? specificAllow : specificDisallow)
                            : inStar ? (key.equals("allow") ? allow : disallow) : null;
                    if (target != null) {
                        target.add(value.replace("*", ""));
                    }
                }
            }
            return specificAllow.isEmpty() && specificDisallow.isEmpty() ? new Robots(allow, disallow) : new Robots(specificAllow, specificDisallow);
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final Map<String, Robots> robots = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequest = new ConcurrentHashMap<>();
    private final String userAgent;
    private final long minDelayMs;

    public PoliteFetcher(AppProperties props) {
        this.userAgent = props.fetch() == null ? "ielts-prep/1.0" : props.fetch().userAgent();
        this.minDelayMs = props.fetch() == null ? 1500 : props.fetch().minDelayMs();
    }

    public Optional<Fetched> fetch(String url) {
        URI uri = URI.create(url);
        String host = uri.getScheme() + "://" + uri.getHost();
        Robots rules = robots.computeIfAbsent(host, this::loadRobots);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (!rules.allowed(path)) {
            log.info("robots.txt disallows {} — skipped", url);
            return Optional.empty();
        }
        try {
            HttpResponse<byte[]> res = send(uri);
            if (res.statusCode() >= 400) {
                log.warn("GET {} → {}", url, res.statusCode());
                return Optional.empty();
            }
            return Optional.of(new Fetched(url, res.statusCode(), res.headers().firstValue("content-type").orElse(""), res.body()));
        } catch (IOException e) {
            log.warn("GET {} failed: {}", url, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Robots loadRobots(String host) {
        try {
            HttpResponse<byte[]> res = send(URI.create(host + "/robots.txt"));
            if (res.statusCode() >= 400) {
                return new Robots(List.of(), List.of());
            }
            return Robots.parse(new String(res.body(), StandardCharsets.UTF_8), "ielts-prep");
        } catch (Exception e) {
            return new Robots(List.of(), List.of());
        }
    }

    private HttpResponse<byte[]> send(URI uri) throws IOException, InterruptedException {
        String host = uri.getHost();
        synchronized (this) {
            long wait = lastRequest.getOrDefault(host, 0L) + minDelayMs - System.currentTimeMillis();
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastRequest.put(host, System.currentTimeMillis());
        }
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).header("User-Agent", userAgent)
                .header("Accept", "text/html,application/pdf,application/xml,application/json;q=0.9,*/*;q=0.5").GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofByteArray());
    }
}
