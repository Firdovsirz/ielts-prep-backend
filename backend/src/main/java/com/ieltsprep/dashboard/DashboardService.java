package com.ieltsprep.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.band.BandCalculator;
import com.ieltsprep.claude.SpendGuard;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.Skill;
import com.ieltsprep.dashboard.DashboardDtos.Activity;
import com.ieltsprep.dashboard.DashboardDtos.BandPoint;
import com.ieltsprep.dashboard.DashboardDtos.BlankStat;
import com.ieltsprep.dashboard.DashboardDtos.CriteriaPoint;
import com.ieltsprep.dashboard.DashboardDtos.DashboardView;
import com.ieltsprep.dashboard.DashboardDtos.DayActivity;
import com.ieltsprep.dashboard.DashboardDtos.HistoryRow;
import com.ieltsprep.dashboard.DashboardDtos.ModuleBands;
import com.ieltsprep.dashboard.DashboardDtos.QuestionTypeAccuracy;
import com.ieltsprep.dashboard.DashboardDtos.TimeStat;
import com.ieltsprep.errorlog.ErrorLogService;
import com.ieltsprep.marking.QuestionMarker.QuestionResult;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.PracticeSessionRepository;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.settings.SettingsService;
import com.ieltsprep.settings.UserSettings;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Aggregates the whole attempt history into the Progress dashboard. */
@Service
public class DashboardService {

    /** How many recent band points form the "current" estimate for a module. */
    static final int CURRENT_WINDOW = 3;

    private final PracticeSessionRepository sessions;
    private final AttemptRepository attempts;
    private final ItemRepository items;
    private final ErrorLogService errorLog;
    private final SettingsService settings;
    private final SpendGuard spend;
    private final BandCalculator bands;
    private final Clock clock;

    public DashboardService(PracticeSessionRepository sessions, AttemptRepository attempts, ItemRepository items,
            ErrorLogService errorLog, SettingsService settings, SpendGuard spend, BandCalculator bands, Clock clock) {
        this.sessions = sessions;
        this.attempts = attempts;
        this.items = items;
        this.errorLog = errorLog;
        this.settings = settings;
        this.spend = spend;
        this.bands = bands;
        this.clock = clock;
    }

    public DashboardView dashboard() {
        UserSettings s = settings.get();
        List<PracticeSession> completed = sessions.findByStatusOrderByStartedAtAsc(SessionStatus.COMPLETED);
        List<Attempt> all = attempts.findAllByOrderBySubmittedAtAsc();
        Map<String, List<BandPoint>> history = bandHistory(completed, all);
        ModuleBands current = current(history);
        LocalDate today = LocalDate.now(clock);
        Integer daysToTest = s.getTestDate() == null ? null : (int) ChronoUnit.DAYS.between(today, s.getTestDate());
        return new DashboardView(s.getTargetBand().doubleValue(), s.getCurrentBand().doubleValue(), s.getTestDate(), daysToTest, current,
                history, criteriaHistory(all), questionTypes(all), errorLog.summary().stream().limit(10).toList(), timing(all),
                blanks(completed, all), activity(completed, all));
    }

    Map<String, List<BandPoint>> bandHistory(List<PracticeSession> completed, List<Attempt> all) {
        Map<String, List<BandPoint>> out = new LinkedHashMap<>();
        for (Skill m : List.of(Skill.LISTENING, Skill.READING, Skill.WRITING, Skill.SPEAKING)) {
            out.put(m.name(), new ArrayList<>());
        }
        for (PracticeSession p : completed) {
            if ((p.getModule() == Skill.READING || p.getModule() == Skill.LISTENING) && p.getBandEstimate() != null) {
                boolean full = p.getKind() == SessionKind.READING_TEST || p.getKind() == SessionKind.LISTENING_TEST;
                out.get(p.getModule().name()).add(new BandPoint(p.getFinishedAt(), p.getBandEstimate().doubleValue(), !full, p.getId(), p.getKind().name()));
            }
        }
        Map<Long, PracticeSession> byId = completed.stream().collect(Collectors.toMap(PracticeSession::getId, x -> x));
        for (Attempt a : all) {
            if ((a.getModule() == Skill.WRITING || a.getModule() == Skill.SPEAKING) && a.getStatus() == AttemptStatus.GRADED
                    && a.getBandEstimate() != null) {
                PracticeSession p = byId.get(a.getSessionId());
                boolean full = p != null && (p.getKind() == SessionKind.WRITING_TEST || p.getKind() == SessionKind.SPEAKING_TEST);
                out.get(a.getModule().name()).add(new BandPoint(a.getGradedAt() == null ? a.getSubmittedAt() : a.getGradedAt(),
                        a.getBandEstimate().doubleValue(), !full, a.getSessionId(), a.getTaskType()));
            }
        }
        out.values().forEach(l -> l.sort(Comparator.comparing(BandPoint::at)));
        return out;
    }

    /** Current band per module: mean of the latest points (full tests preferred), rounded to the nearest half band. */
    ModuleBands current(Map<String, List<BandPoint>> history) {
        Map<String, Double> cur = new HashMap<>();
        history.forEach((module, points) -> {
            if (points.isEmpty()) {
                return;
            }
            List<BandPoint> full = points.stream().filter(p -> !p.estimate()).toList();
            List<BandPoint> basis = full.size() >= 2 ? full : points;
            List<BandPoint> recent = basis.subList(Math.max(0, basis.size() - CURRENT_WINDOW), basis.size());
            cur.put(module, bands.roundToHalf(recent.stream().mapToDouble(BandPoint::band).average().orElse(0)));
        });
        Double l = cur.get("LISTENING");
        Double r = cur.get("READING");
        Double w = cur.get("WRITING");
        Double sp = cur.get("SPEAKING");
        Double overall = l != null && r != null && w != null && sp != null ? bands.overallBand(l, r, w, sp) : null;
        return new ModuleBands(l, r, w, sp, overall, true);
    }

    Map<String, List<CriteriaPoint>> criteriaHistory(List<Attempt> all) {
        Map<String, List<CriteriaPoint>> out = new LinkedHashMap<>();
        out.put("WRITING", new ArrayList<>());
        out.put("SPEAKING", new ArrayList<>());
        for (Attempt a : all) {
            if (a.getStatus() == AttemptStatus.GRADED && a.getPerCriterionBands() != null && out.containsKey(a.getModule().name())) {
                Map<String, Integer> b = Json.read(a.getPerCriterionBands(), new TypeReference<Map<String, Integer>>() {});
                out.get(a.getModule().name()).add(new CriteriaPoint(a.getGradedAt() == null ? a.getSubmittedAt() : a.getGradedAt(),
                        a.getId(), a.getTaskType(), b));
            }
        }
        return out;
    }

    /** Accuracy per question type (the weak-area detector), with accuracy over the most recent 30 answers. */
    List<QuestionTypeAccuracy> questionTypes(List<Attempt> all) {
        record Key(String module, String type) {}
        Map<Key, List<Boolean>> outcomes = new LinkedHashMap<>();
        for (Attempt a : all) {
            if ((a.getModule() == Skill.READING || a.getModule() == Skill.LISTENING) && a.getPerQuestion() != null) {
                for (QuestionResult r : Json.read(a.getPerQuestion(), new TypeReference<List<QuestionResult>>() {})) {
                    outcomes.computeIfAbsent(new Key(a.getModule().name(), r.questionType().name()), k -> new ArrayList<>()).add(r.correct());
                }
            }
        }
        return outcomes.entrySet().stream().map(e -> {
            List<Boolean> list = e.getValue();
            int correct = (int) list.stream().filter(Boolean::booleanValue).count();
            List<Boolean> recent = list.subList(Math.max(0, list.size() - 30), list.size());
            double recentAcc = recent.stream().filter(Boolean::booleanValue).count() / (double) recent.size();
            return new QuestionTypeAccuracy(e.getKey().module(), e.getKey().type(), correct, list.size(),
                    round(correct / (double) list.size()), list.size() > 30 ? round(recentAcc) : null);
        }).sorted(Comparator.comparingDouble(QuestionTypeAccuracy::accuracy)).toList();
    }

    List<TimeStat> timing(List<Attempt> all) {
        return List.of(
                timeStat("Reading passage", all, a -> a.getModule() == Skill.READING, 20 * 60),
                timeStat("Listening section", all, a -> a.getModule() == Skill.LISTENING, 0),
                timeStat("Writing Task 1", all, a -> a.getModule() == Skill.WRITING && !a.getTaskType().equals("WRITING_TASK2"), 20 * 60),
                timeStat("Writing Task 2", all, a -> a.getModule() == Skill.WRITING && a.getTaskType().equals("WRITING_TASK2"), 40 * 60));
    }

    private TimeStat timeStat(String label, List<Attempt> all, java.util.function.Predicate<Attempt> filter, int limit) {
        List<Integer> secs = all.stream().filter(filter).map(Attempt::getDurationSeconds).filter(java.util.Objects::nonNull).toList();
        return new TimeStat(label, secs.size(), secs.isEmpty() ? null : secs.stream().mapToInt(Integer::intValue).average().orElse(0), limit);
    }

    /** Blank answers by position in exam-mode full tests: blanks piling up in the last passage/section = time pressure. */
    List<BlankStat> blanks(List<PracticeSession> completed, List<Attempt> all) {
        Map<Long, List<Attempt>> bySession = all.stream().collect(Collectors.groupingBy(Attempt::getSessionId));
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (PracticeSession p : completed) {
            boolean fullExam = p.getMode() == SessionMode.EXAM
                    && (p.getKind() == SessionKind.READING_TEST || p.getKind() == SessionKind.LISTENING_TEST);
            if (!fullExam) {
                continue;
            }
            List<Attempt> parts = bySession.getOrDefault(p.getId(), List.of());
            for (int i = 0; i < parts.size(); i++) {
                List<QuestionResult> results = Json.read(parts.get(i).getPerQuestion(), new TypeReference<List<QuestionResult>>() {});
                String label = (p.getModule() == Skill.READING ? "Reading passage " : "Listening section ") + (i + 1);
                int[] c = counts.computeIfAbsent(label, k -> new int[2]);
                c[0] += results.size();
                c[1] += (int) results.stream().filter(QuestionResult::blank).count();
            }
        }
        return counts.entrySet().stream()
                .map(e -> new BlankStat(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[0] == 0 ? 0 : round(e.getValue()[1] / (double) e.getValue()[0])))
                .toList();
    }

    Activity activity(List<PracticeSession> completed, List<Attempt> all) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Map<LocalDate, int[]> byDay = new HashMap<>();
        int totalSeconds = 0;
        for (PracticeSession p : completed) {
            LocalDate d = p.getStartedAt().atZone(zone).toLocalDate();
            int secs = p.getTimeUsedSeconds() == null ? 0 : p.getTimeUsedSeconds();
            totalSeconds += secs;
            int[] c = byDay.computeIfAbsent(d, k -> new int[2]);
            c[0] += secs;
            c[1]++;
        }
        Set<LocalDate> active = new TreeSet<>(byDay.keySet());
        int streak = 0;
        LocalDate cursor = active.contains(today) ? today : today.minusDays(1);
        while (active.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : active) {
            run = prev != null && prev.plusDays(1).equals(d) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d;
        }
        List<DayActivity> last28 = new ArrayList<>();
        for (int i = 27; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            int[] c = byDay.getOrDefault(d, new int[2]);
            last28.add(new DayActivity(d, Math.round(c[0] / 60f), c[1]));
        }
        SpendGuard.Status st = spend.status();
        return new Activity(streak, longest, Math.round(totalSeconds / 60f), all.size(), completed.size(), last28,
                st.spentToday().doubleValue(), st.spentLast7Days().doubleValue());
    }

    public List<HistoryRow> history() {
        Map<Long, String> titles = new HashMap<>();
        return sessions.findTop50ByOrderByStartedAtDesc().stream().map(p -> {
            String title = p.itemIdList().isEmpty() ? null
                    : titles.computeIfAbsent(p.itemIdList().getFirst(), id -> items.findById(id).map(Item::getTitle).orElse(null));
            return new HistoryRow(p.getId(), p.getModule().name(), p.getKind().name(), p.getMode().name(), p.getStartedAt(),
                    p.getFinishedAt(), p.getTimeUsedSeconds(), p.getRawScore(), p.getMaxScore(),
                    p.getBandEstimate() == null ? null : p.getBandEstimate().doubleValue(), p.getStatus().name(), title);
        }).toList();
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
