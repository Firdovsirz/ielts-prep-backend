package com.ieltsprep.errorlog;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure grouping/ranking of the error log: frequency weighted by recency, trend over the latest graded pieces, and the
 * "resolved" rule (a subtype absent from the last N graded pieces).
 */
public final class ErrorAnalytics {

    private ErrorAnalytics() {}

    public record Row(long id, long attemptId, String type, String subtype, String area, Instant createdAt, Instant resolvedAt,
            String original, String correction) {}

    public enum Trend { NEW, WORSENING, IMPROVING, STABLE, RESOLVED }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Example(long errorId, long attemptId, String original, String correction) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(name = "ErrorSubtypeStats")
    public record SubtypeStats(
            String type,
            String subtype,
            @Schema(nullable = true) String area,
            int total,
            int unresolved,
            int recent,
            int previous,
            Instant firstSeen,
            Instant lastSeen,
            boolean resolved,
            Trend trend,
            double score,
            List<Example> examples) {}

    /** Half-life style decay: an error from three weeks ago counts ~0.37 of one from today. */
    static final double DECAY_DAYS = 21.0;

    /**
     * @param gradedNewestFirst ids of graded Writing/Speaking attempts, newest first
     * @param window            number of graded pieces compared for the trend
     */
    public static List<SubtypeStats> group(List<Row> rows, List<Long> gradedNewestFirst, Instant now, int window) {
        Map<Long, Integer> position = new HashMap<>();
        for (int i = 0; i < gradedNewestFirst.size(); i++) {
            position.put(gradedNewestFirst.get(i), i);
        }
        Map<String, List<Row>> bySubtype = rows.stream()
                .collect(Collectors.groupingBy(r -> r.type() + "/" + r.subtype(), LinkedHashMap::new, Collectors.toList()));
        List<SubtypeStats> out = new ArrayList<>();
        for (List<Row> group : bySubtype.values()) {
            Row any = group.getFirst();
            int recent = 0;
            int previous = 0;
            int olderThanWindow = 0;
            double score = 0;
            for (Row r : group) {
                Integer pos = position.get(r.attemptId());
                if (pos != null && pos < window) {
                    recent++;
                } else if (pos != null && pos < 2 * window) {
                    previous++;
                }
                if (pos == null || pos >= window) {
                    olderThanWindow++;
                }
                double ageDays = Math.max(0, Duration.between(r.createdAt(), now).toHours() / 24.0);
                score += Math.exp(-ageDays / DECAY_DAYS);
            }
            int unresolved = (int) group.stream().filter(r -> r.resolvedAt() == null).count();
            boolean resolved = unresolved == 0;
            Trend trend;
            if (resolved) {
                trend = Trend.RESOLVED;
            } else if (olderThanWindow == 0) {
                trend = Trend.NEW;
            } else if (recent > previous) {
                trend = Trend.WORSENING;
            } else if (recent < previous) {
                trend = Trend.IMPROVING;
            } else {
                trend = Trend.STABLE;
            }
            score += 0.5 * recent;
            if (resolved) {
                score *= 0.25; // still re-tested occasionally, but ranked low
            }
            List<Row> newest = group.stream().sorted(Comparator.comparing(Row::createdAt).reversed()).toList();
            out.add(new SubtypeStats(any.type(), any.subtype(), any.area(), group.size(), unresolved, recent, previous,
                    newest.getLast().createdAt(), newest.getFirst().createdAt(), resolved, trend, Math.round(score * 100) / 100.0,
                    newest.stream().limit(3).map(r -> new Example(r.id(), r.attemptId(), r.original(), r.correction())).toList()));
        }
        out.sort(Comparator.comparingDouble(SubtypeStats::score).reversed().thenComparing(SubtypeStats::subtype));
        return out;
    }

    /**
     * Subtypes that should now be marked resolved: unresolved, but absent from every one of the last {@code n} graded
     * pieces (and at least {@code n} pieces have been graded since it was first seen).
     */
    public static Set<String> newlyResolved(List<Row> rows, List<Long> gradedNewestFirst, int n) {
        if (gradedNewestFirst.size() < n) {
            return Set.of();
        }
        Set<Long> lastN = Set.copyOf(gradedNewestFirst.subList(0, n));
        return rows.stream().filter(r -> r.resolvedAt() == null).map(Row::subtype).distinct()
                .filter(sub -> rows.stream().filter(r -> r.subtype().equals(sub)).noneMatch(r -> lastN.contains(r.attemptId())))
                .collect(Collectors.toSet());
    }
}
