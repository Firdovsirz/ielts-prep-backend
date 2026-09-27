package com.ieltsprep.coach;

import com.ieltsprep.coach.CoachReportContent.Priority;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule-based weekly report used when no Claude API key is configured (or the call fails). Less nuanced than the AI
 * coach, but every sentence is derived from the same statistics.
 */
public final class CoachFallback {

    static final List<String> SKILLS = List.of("LISTENING", "READING", "WRITING", "SPEAKING");

    private CoachFallback() {}

    public static CoachReportContent write(WeeklyStats s) {
        Double overall = s.currentBands().overall();
        List<String> practised = SKILLS.stream().filter(m -> s.modules().get(m).sessions() > 0).map(CoachFallback::name).toList();
        List<String> missed = SKILLS.stream().filter(m -> s.modules().get(m).sessions() == 0).map(CoachFallback::name).toList();

        String headline = s.sessions() == 0 ? "A quiet week — time to restart the routine"
                : s.minutes() >= 10 ? s.sessions() + " sessions and " + s.minutes() + " minutes of practice this week"
                        : s.sessions() + (s.sessions() == 1 ? " practice session" : " practice sessions") + " this week";

        StringBuilder summary = new StringBuilder();
        if (s.sessions() == 0) {
            summary.append("No practice sessions were completed between ").append(s.weekStart()).append(" and ").append(s.weekEnd()).append(". ");
        } else {
            summary.append("You completed ").append(s.sessions()).append(" sessions").append(s.minutes() >= 10 ? " (" + s.minutes() + " minutes)" : "").append(" on ")
                    .append(s.activeDays()).append(s.activeDays() == 1 ? " day" : " days").append(", practising ")
                    .append(practised.isEmpty() ? "grammar and vocabulary" : String.join(", ", practised)).append(". ");
        }
        SKILLS.forEach(m -> {
            WeeklyStats.ModuleWeek w = s.modules().get(m);
            if (w.bestBand() != null) {
                summary.append(name(m)).append(" results averaged ").append(fmt(w.averageBand())).append(" (best ").append(fmt(w.bestBand())).append("). ");
            }
        });
        if (s.plan().tasks() > 0) {
            summary.append("You finished ").append(s.plan().done()).append(" of ").append(s.plan().tasks()).append(" planned tasks (")
                    .append(Math.round(s.plan().completion() * 100)).append("%). ");
        }
        if (s.vocab().reviews() > 0) {
            summary.append("You reviewed ").append(s.vocab().reviews()).append(" vocabulary cards.");
        }

        String outlook;
        if (overall == null) {
            outlook = "There is not yet a result for every skill, so no overall estimate is possible — a full mock test will give one.";
        } else {
            double gap = s.targetBand() - overall;
            outlook = "Your current overall estimate is " + fmt(overall) + " against a target of " + fmt(s.targetBand())
                    + (s.daysToTest() == null ? "." : ", with " + s.daysToTest() + " days to go.")
                    + (gap <= 0 ? " You are at target — protect it with timed practice and steady routines."
                            : gap <= 0.5 ? " The target is within reach if the weakest skill improves by half a band."
                                    : " Closing a gap of " + fmt(gap) + " needs focused work on the weakest skills; a realistic aim is "
                                            + fmt(Math.min(s.targetBand(), overall + 0.5)) + " by test day.");
        }

        List<String> wins = new ArrayList<>();
        SKILLS.forEach(m -> {
            WeeklyStats.ModuleWeek w = s.modules().get(m);
            if (w.bestBand() != null && w.bestBand() >= s.targetBand()) {
                wins.add("You reached band " + fmt(w.bestBand()) + " in " + name(m) + " — at or above your target.");
            }
        });
        s.errorPatterns().stream().filter(e -> e.trend().equals("IMPROVING") || e.trend().equals("RESOLVED")).limit(2)
                .forEach(e -> wins.add("Fewer " + human(e.subtype()) + " errors in your recent writing and speaking."));
        if (s.vocab().reviews() >= 50) {
            wins.add(s.vocab().reviews() + " vocabulary reviews kept your spaced-repetition deck on track.");
        }
        if (s.streakDays() >= 3) {
            wins.add("A " + s.streakDays() + "-day study streak.");
        }
        if (wins.isEmpty()) {
            wins.add(s.sessions() > 0 ? "You kept practising — consistency is what moves bands." : "Your plan and deck are ready for next week.");
        }

        List<String> concerns = new ArrayList<>();
        if (!missed.isEmpty() && s.sessions() > 0) {
            concerns.add("No practice this week in " + String.join(", ", missed) + ".");
        }
        s.errorPatterns().stream().filter(e -> e.trend().equals("WORSENING") || e.trend().equals("NEW")).limit(2)
                .forEach(e -> concerns.add(human(e.subtype()) + " errors are " + (e.trend().equals("NEW") ? "appearing" : "becoming more frequent")
                        + " (" + e.total() + " so far)."));
        lowest(s.writingCriteria()).ifPresent(e -> concerns.add("Writing: " + human(e.getKey()) + " is your lowest criterion (" + fmt(e.getValue()) + ")."));
        s.weakQuestionTypes().stream().limit(1).forEach(w -> concerns.add(name(w.module()) + ": " + human(w.questionType()) + " questions are at "
                + Math.round(w.accuracy() * 100) + "% accuracy."));
        if (s.vocab().due() > 30) {
            concerns.add(s.vocab().due() + " vocabulary cards are overdue.");
        }
        if (concerns.isEmpty()) {
            concerns.add("Nothing alarming — keep the balance between timed tests and targeted practice.");
        }

        List<Priority> priorities = new ArrayList<>();
        String weakest = SKILLS.stream().min(Comparator.comparingDouble(m -> {
            Double b = band(s, m);
            return b == null ? -1 : b;
        })).orElse("WRITING");
        Double wb = band(s, weakest);
        priorities.add(new Priority(weakest, "Put most practice time into " + name(weakest),
                (wb == null ? "You have no " + name(weakest) + " result yet, so take one this week. "
                        : name(weakest) + " is at " + fmt(wb) + ", your furthest skill from " + fmt(s.targetBand()) + ". ")
                        + "Follow the study plan's " + name(weakest) + " tasks and review every piece of feedback."));
        s.errorPatterns().stream().filter(e -> !e.resolved() && e.type().equals("grammar")).findFirst()
                .ifPresentOrElse(e -> priorities.add(new Priority("GRAMMAR", "Drill " + human(e.subtype()),
                        "It has appeared " + e.total() + " times in your graded work. Run the error drill in Grammar until it stops recurring.")),
                        () -> priorities.add(new Priority("GRAMMAR", "Keep grammar accurate under time pressure",
                                "Do one grammar exercise set every other day from your weakest area.")));
        priorities.add(s.vocab().total() == 0
                ? new Priority("VOCAB", "Start a vocabulary deck", "Add two topic word banks and review the due cards daily.")
                : new Priority("VOCAB", "Review due vocabulary every day", "Ten minutes a day keeps " + s.vocab().total()
                        + " words active for Writing Task 2 and Speaking Part 3."));

        String encouragement = s.sessions() == 0 ? "One short session tomorrow is enough to restart — the plan is waiting."
                : "Steady, focused weeks like this add up — keep following the plan.";
        return new CoachReportContent(headline, summary.toString().trim(), outlook, wins, concerns, priorities, encouragement);
    }

    private static Double band(WeeklyStats s, String m) {
        return switch (m) {
            case "LISTENING" -> s.currentBands().listening();
            case "READING" -> s.currentBands().reading();
            case "WRITING" -> s.currentBands().writing();
            default -> s.currentBands().speaking();
        };
    }

    private static java.util.Optional<Map.Entry<String, Double>> lowest(Map<String, Double> criteria) {
        return criteria == null ? java.util.Optional.empty() : criteria.entrySet().stream().min(Map.Entry.comparingByValue());
    }

    static String name(String module) {
        return module.charAt(0) + module.substring(1).toLowerCase(Locale.ROOT);
    }

    static String human(String key) {
        return key.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    static String fmt(Double v) {
        return v == null ? "—" : String.format(Locale.ROOT, "%.1f", v);
    }
}
