package com.ieltsprep.coach;

import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.content.Skill;
import com.ieltsprep.dashboard.DashboardDtos.CriteriaPoint;
import com.ieltsprep.dashboard.DashboardDtos.DashboardView;
import com.ieltsprep.dashboard.DashboardService;
import com.ieltsprep.errorlog.ErrorEntryRepository;
import com.ieltsprep.grammar.GrammarDtos;
import com.ieltsprep.grammar.GrammarService;
import com.ieltsprep.plan.PlanTask;
import com.ieltsprep.plan.PlanTaskRepository;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.PracticeSessionRepository;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.vocab.VocabCardRepository;
import com.ieltsprep.vocab.VocabReviewRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Computes {@link WeeklyStats} for a date range (inclusive) from sessions, attempts, the error log, grammar and vocabulary. */
@Service
public class CoachStatsService {

    static final List<String> MODULES = List.of("LISTENING", "READING", "WRITING", "SPEAKING", "GRAMMAR", "VOCAB");

    private final PracticeSessionRepository sessions;
    private final AttemptRepository attempts;
    private final DashboardService dashboard;
    private final GrammarService grammar;
    private final ErrorEntryRepository errors;
    private final VocabCardRepository cards;
    private final VocabReviewRepository reviews;
    private final PlanTaskRepository planTasks;
    private final Clock clock;

    public CoachStatsService(PracticeSessionRepository sessions, AttemptRepository attempts, DashboardService dashboard, GrammarService grammar,
            ErrorEntryRepository errors, VocabCardRepository cards, VocabReviewRepository reviews, PlanTaskRepository planTasks, Clock clock) {
        this.sessions = sessions;
        this.attempts = attempts;
        this.dashboard = dashboard;
        this.grammar = grammar;
        this.errors = errors;
        this.cards = cards;
        this.reviews = reviews;
        this.planTasks = planTasks;
        this.clock = clock;
    }

    public WeeklyStats compute(LocalDate from, LocalDate to) {
        ZoneId zone = clock.getZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        DashboardView dash = dashboard.dashboard();

        List<PracticeSession> week = sessions.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(start).stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED && s.getStartedAt().isBefore(end)).toList();
        Map<String, List<Double>> bands = new LinkedHashMap<>();
        week.stream().filter(s -> (s.getModule() == Skill.READING || s.getModule() == Skill.LISTENING) && s.getBandEstimate() != null)
                .forEach(s -> bands.computeIfAbsent(s.getModule().name(), k -> new ArrayList<>()).add(s.getBandEstimate().doubleValue()));
        for (Attempt a : attempts.findBySubmittedAtGreaterThanEqualOrderBySubmittedAtAsc(start)) {
            if (a.getSubmittedAt().isBefore(end) && a.getStatus() == AttemptStatus.GRADED && a.getBandEstimate() != null
                    && (a.getModule() == Skill.WRITING || a.getModule() == Skill.SPEAKING)) {
                bands.computeIfAbsent(a.getModule().name(), k -> new ArrayList<>()).add(a.getBandEstimate().doubleValue());
            }
        }
        Map<String, WeeklyStats.ModuleWeek> modules = new LinkedHashMap<>();
        int totalMinutes = 0;
        for (String m : MODULES) {
            List<PracticeSession> ms = week.stream().filter(s -> s.getModule().name().equals(m)).toList();
            int minutes = ms.stream().mapToInt(CoachStatsService::minutes).sum();
            totalMinutes += minutes;
            List<Double> b = bands.getOrDefault(m, List.of());
            modules.put(m, new WeeklyStats.ModuleWeek(ms.size(), minutes, b.stream().max(Double::compare).orElse(null),
                    b.isEmpty() ? null : Math.round(b.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0));
        }
        int activeDays = (int) dash.activity().last28Days().stream()
                .filter(d -> !d.date().isBefore(from) && !d.date().isAfter(to) && (d.minutes() > 0 || d.sessions() > 0)).count();

        List<WeeklyStats.ErrorTrend> patterns = dash.topErrors().stream().limit(8)
                .map(e -> new WeeklyStats.ErrorTrend(e.type(), e.subtype(), e.total(), e.recent(), e.trend().name(), e.resolved())).toList();
        List<WeeklyStats.WeakType> weak = dash.questionTypes().stream().filter(q -> q.total() >= 3 && q.accuracy() < 0.8).limit(5)
                .map(q -> new WeeklyStats.WeakType(q.module(), q.questionType(), q.accuracy(), q.total())).toList();
        GrammarDtos.Overview g = grammar.overview();
        List<WeeklyStats.AreaStat> areas = g.areas().stream().filter(a -> a.proficiency() != null || a.openErrors() > 0)
                .sorted(Comparator.comparingDouble(a -> a.proficiency() == null ? 50 : a.proficiency())).limit(4)
                .map(a -> new WeeklyStats.AreaStat(a.name(), a.proficiency(), a.openErrors())).toList();

        Instant now = Instant.now(clock);
        WeeklyStats.VocabWeek vocab = new WeeklyStats.VocabWeek(reviews.countByReviewedAtBetween(start, end), cards.countByCreatedAtBetween(start, end),
                cards.countBySuspendedFalseAndIntervalDaysGreaterThanEqual(21), cards.countBySuspendedFalseAndDueAtLessThanEqual(now),
                cards.countBySuspendedFalse());
        List<PlanTask> plan = planTasks.findByPlanDateBetweenOrderByPlanDateAscPriorityAscIdAsc(from, to).stream()
                .filter(t -> !"REST".equals(t.getAction())).toList();
        int done = (int) plan.stream().filter(PlanTask::isDone).count();
        WeeklyStats.PlanWeek planWeek = new WeeklyStats.PlanWeek(plan.size(), done, plan.isEmpty() ? 0 : Math.round(done * 100.0 / plan.size()) / 100.0);

        return new WeeklyStats(from, to, dash.daysToTest(), dash.targetBand(), dash.current(), modules, week.size(), totalMinutes, activeDays,
                dash.activity().streakDays(), criteria(dash.criteriaHistory().get("WRITING"), start, end),
                criteria(dash.criteriaHistory().get("SPEAKING"), start, end), patterns, errors.countByCreatedAtBetween(start, end),
                errors.countByResolvedAtBetween(start, end), weak, areas, vocab, planWeek, dash.activity().apiSpendWeek());
    }

    static int minutes(PracticeSession s) {
        if (s.getTimeUsedSeconds() != null) {
            return Math.round(s.getTimeUsedSeconds() / 60f);
        }
        if (s.getFinishedAt() == null) {
            return 0;
        }
        long m = Duration.between(s.getStartedAt(), s.getFinishedAt()).toMinutes();
        return (int) Math.min(m, 180);
    }

    static Map<String, Double> criteria(List<CriteriaPoint> points, Instant start, Instant end) {
        Map<String, List<Integer>> values = new LinkedHashMap<>();
        if (points != null) {
            points.stream().filter(p -> !p.at().isBefore(start) && p.at().isBefore(end)).forEach(p -> p.bands().forEach((k, v) -> {
                if (v != null && v > 0) {
                    values.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
                }
            }));
        }
        Map<String, Double> out = new LinkedHashMap<>();
        values.forEach((k, v) -> out.put(k, Math.round(v.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0));
        return out;
    }
}
