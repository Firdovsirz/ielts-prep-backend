package com.ieltsprep.dashboard;

import com.ieltsprep.errorlog.ErrorAnalytics;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {}

    @Schema(name = "ModuleBands")
    public record ModuleBands(@Schema(nullable = true) Double listening, @Schema(nullable = true) Double reading,
            @Schema(nullable = true) Double writing, @Schema(nullable = true) Double speaking, @Schema(nullable = true) Double overall,
            boolean overallIsEstimate) {}

    /** One band observation. estimate = derived from a partial test (single passage/section/task). */
    @Schema(name = "BandPoint")
    public record BandPoint(Instant at, double band, boolean estimate, long sessionId, String kind) {}

    @Schema(name = "CriteriaPoint")
    public record CriteriaPoint(Instant at, long attemptId, String taskType, Map<String, Integer> bands) {}

    @Schema(name = "QuestionTypeAccuracy")
    public record QuestionTypeAccuracy(String module, String questionType, int correct, int total, double accuracy,
            @Schema(nullable = true) Double recentAccuracy) {}

    @Schema(name = "TimeStat")
    public record TimeStat(String label, int count, @Schema(nullable = true) Double averageSeconds, int limitSeconds) {}

    @Schema(name = "BlankStat")
    public record BlankStat(String label, int questions, int blanks, double blankRate) {}

    @Schema(name = "DayActivity")
    public record DayActivity(LocalDate date, int minutes, int sessions) {}

    @Schema(name = "Activity")
    public record Activity(int streakDays, int longestStreak, int totalMinutes, int itemsCompleted, int sessionsCompleted,
            List<DayActivity> last28Days, double apiSpendToday, double apiSpendWeek) {}

    @Schema(name = "DashboardView")
    public record DashboardView(
            double targetBand,
            double startingBand,
            @Schema(nullable = true) LocalDate testDate,
            @Schema(nullable = true) Integer daysToTest,
            ModuleBands current,
            Map<String, List<BandPoint>> bandHistory,
            Map<String, List<CriteriaPoint>> criteriaHistory,
            List<QuestionTypeAccuracy> questionTypes,
            List<ErrorAnalytics.SubtypeStats> topErrors,
            List<TimeStat> timing,
            List<BlankStat> blanks,
            Activity activity) {}

    @Schema(name = "HistoryRow")
    public record HistoryRow(long sessionId, String module, String kind, String mode, Instant startedAt,
            @Schema(nullable = true) Instant finishedAt, @Schema(nullable = true) Integer timeUsedSeconds,
            @Schema(nullable = true) Integer rawScore, @Schema(nullable = true) Integer maxScore,
            @Schema(nullable = true) Double band, String status, @Schema(nullable = true) String title) {}
}
