package com.ieltsprep.coach;

import com.ieltsprep.dashboard.DashboardDtos.ModuleBands;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The numbers behind a weekly coach report (also sent to Claude as JSON). */
@Schema(name = "WeeklyStats")
public record WeeklyStats(
        LocalDate weekStart,
        LocalDate weekEnd,
        @Schema(nullable = true) Integer daysToTest,
        double targetBand,
        ModuleBands currentBands,
        Map<String, ModuleWeek> modules,
        int sessions,
        int minutes,
        int activeDays,
        int streakDays,
        Map<String, Double> writingCriteria,
        Map<String, Double> speakingCriteria,
        List<ErrorTrend> errorPatterns,
        long errorsLogged,
        long errorsResolved,
        List<WeakType> weakQuestionTypes,
        List<AreaStat> weakestGrammarAreas,
        VocabWeek vocab,
        PlanWeek plan,
        double apiSpendWeek) {

    @Schema(name = "WeeklyModuleStats")
    public record ModuleWeek(int sessions, int minutes, @Schema(nullable = true) Double bestBand, @Schema(nullable = true) Double averageBand) {}

    @Schema(name = "WeeklyErrorTrend")
    public record ErrorTrend(String type, String subtype, int total, int recent, String trend, boolean resolved) {}

    @Schema(name = "WeeklyWeakType")
    public record WeakType(String module, String questionType, double accuracy, int total) {}

    @Schema(name = "WeeklyAreaStat")
    public record AreaStat(String name, @Schema(nullable = true) Double proficiency, int openErrors) {}

    @Schema(name = "WeeklyVocab")
    public record VocabWeek(long reviews, long added, long learned, long due, long total) {}

    @Schema(name = "WeeklyPlan")
    public record PlanWeek(int tasks, int done, double completion) {}
}
