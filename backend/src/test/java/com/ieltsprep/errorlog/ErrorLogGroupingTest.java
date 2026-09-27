package com.ieltsprep.errorlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ieltsprep.errorlog.ErrorAnalytics.Row;
import com.ieltsprep.errorlog.ErrorAnalytics.SubtypeStats;
import com.ieltsprep.errorlog.ErrorAnalytics.Trend;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ErrorLogGroupingTest {

    static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    // graded pieces, newest first: 10 (today) … 5 (5 days ago)
    static final List<Long> GRADED = List.of(10L, 9L, 8L, 7L, 6L, 5L);

    long ids = 1;

    Row row(long attempt, String subtype, int daysAgo) {
        return new Row(ids++, attempt, "grammar", subtype, "AREA", NOW.minus(Duration.ofDays(daysAgo)), null, "orig", "fix");
    }

    Map<String, SubtypeStats> bySubtype(List<Row> rows) {
        return ErrorAnalytics.group(rows, GRADED, NOW, 3).stream().collect(Collectors.toMap(SubtypeStats::subtype, Function.identity()));
    }

    @Test
    void groupsBySubtypeAndRanksByRecencyWeightedFrequency() {
        List<Row> rows = new ArrayList<>();
        // articles: 4 recent occurrences; tense: 4 old occurrences; comma: 1 recent
        for (int i = 0; i < 4; i++) {
            rows.add(row(10, "article_missing", 0));
        }
        for (int i = 0; i < 4; i++) {
            rows.add(row(5, "tense_choice", 60));
        }
        rows.add(row(9, "comma_splice", 1));

        List<SubtypeStats> ranked = ErrorAnalytics.group(rows, GRADED, NOW, 3);
        assertThat(ranked).extracting(SubtypeStats::subtype).containsExactly("article_missing", "comma_splice", "tense_choice");
        assertThat(ranked.getFirst().total()).isEqualTo(4);
        assertThat(ranked.getFirst().examples()).hasSize(3);
    }

    @Test
    void trendsCompareLatestPiecesWithThePreviousWindow() {
        List<Row> rows = new ArrayList<>();
        rows.add(row(10, "worse", 0));
        rows.add(row(9, "worse", 1));
        rows.add(row(6, "worse", 4));               // previous window: 1, recent: 2
        rows.add(row(7, "better", 3));
        rows.add(row(6, "better", 4));
        rows.add(row(5, "better", 5));
        rows.add(row(10, "better", 0));              // previous: 3, recent: 1
        rows.add(row(9, "fresh", 1));                // only in the latest pieces
        rows.add(row(8, "steady", 2));
        rows.add(row(6, "steady", 4));               // 1 vs 1

        Map<String, SubtypeStats> s = bySubtype(rows);
        assertThat(s.get("worse").trend()).isEqualTo(Trend.WORSENING);
        assertThat(s.get("better").trend()).isEqualTo(Trend.IMPROVING);
        assertThat(s.get("fresh").trend()).isEqualTo(Trend.NEW);
        assertThat(s.get("steady").trend()).isEqualTo(Trend.STABLE);
    }

    @Test
    void subtypeAbsentFromLastNPiecesIsResolved() {
        List<Row> rows = List.of(row(5, "old_problem", 5), row(10, "current_problem", 0));
        assertThat(ErrorAnalytics.newlyResolved(rows, GRADED, 5)).containsExactly("old_problem");
        // not enough graded pieces yet → nothing resolves
        assertThat(ErrorAnalytics.newlyResolved(rows, List.of(10L, 9L), 5)).isEmpty();
    }

    @Test
    void resolvedSubtypesRankLowButStayVisible() {
        Row resolved = new Row(99, 5, "grammar", "done", "AREA", NOW.minus(Duration.ofDays(1)), NOW, "a", "b");
        List<SubtypeStats> ranked = ErrorAnalytics.group(List.of(resolved, row(6, "open", 4)), GRADED, NOW, 3);
        assertThat(ranked.getLast().subtype()).isEqualTo("done");
        assertThat(ranked.getLast().trend()).isEqualTo(Trend.RESOLVED);
        assertThat(ranked.getLast().unresolved()).isZero();
    }
}
