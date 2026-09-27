package com.ieltsprep.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rule-based weekly planner. Practice time goes to the skills furthest below the target band (weighted round-robin so
 * every skill still gets practice), the format tightens as the test approaches (single passages and parts → timed full
 * tests → a mock test and a taper), and grammar/vocabulary work is driven by the error log and the SRS deck.
 * Claude may then personalise the draft (see {@link PlanService}); this class is the deterministic fallback.
 */
public final class PlanGenerator {

    public static final List<String> SKILLS = List.of("LISTENING", "READING", "WRITING", "SPEAKING");

    /** Gap weights are capped so one poor (or empty) attempt cannot crowd every other skill out of the week. */
    static final double MAX_GAP = 2.0;

    public enum Phase {
        BUILD("Build"),
        SHARPEN("Sharpen"),
        EXAM_WEEK("Exam week"),
        AFTER("After the test");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Draft(LocalDate date, PlanAction action, String variant, String title, String why, int minutes, int priority) {}

    public record Result(Phase phase, String focus, List<Draft> tasks) {}

    private PlanGenerator() {}

    public static Phase phase(Integer daysToTest) {
        if (daysToTest == null || daysToTest > 21) {
            return Phase.BUILD;
        }
        if (daysToTest < 0) {
            return Phase.AFTER;
        }
        return daysToTest <= 7 ? Phase.EXAM_WEEK : Phase.SHARPEN;
    }

    public static Integer daysToTest(PlanInputs in, LocalDate date) {
        return in.testDate() == null ? null : (int) ChronoUnit.DAYS.between(date, in.testDate());
    }

    /** Gap to target per skill; unmeasured skills get a substantial weight so a first result is collected soon. */
    static Map<String, Double> gaps(PlanInputs in) {
        Map<String, Double> gaps = new LinkedHashMap<>();
        for (String m : SKILLS) {
            Double b = in.bands().get(m);
            double gap = b == null ? Math.max(0.75, in.targetBand() - in.startingBand()) : Math.max(0, in.targetBand() - b);
            gaps.put(m, Math.min(MAX_GAP, gap));
        }
        return gaps;
    }

    static List<String> byGap(Map<String, Double> gaps) {
        List<String> order = new ArrayList<>(SKILLS);
        order.sort(Comparator.comparingDouble((String m) -> -gaps.get(m)).thenComparingInt(SKILLS::indexOf));
        return order;
    }

    /** Smooth weighted round-robin over the skills: picks are proportional to (gap + base) and evenly spread. */
    static final class SlotPicker {
        private final Map<String, Double> weight = new LinkedHashMap<>();
        private final Map<String, Double> current = new HashMap<>();
        private final double sum;

        SlotPicker(Map<String, Double> gaps) {
            byGap(gaps).forEach(m -> weight.put(m, gaps.get(m) + 0.35));
            sum = weight.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        /** The next skill, skipping any in {@code exclude} (e.g. already twice on this day). */
        String next(java.util.Set<String> exclude) {
            weight.forEach((m, w) -> current.merge(m, w, Double::sum));
            String pick = current.entrySet().stream().filter(e -> !exclude.contains(e.getKey()))
                    .max(Map.Entry.<String, Double>comparingByValue().thenComparing(e -> -SKILLS.indexOf(e.getKey())))
                    .map(Map.Entry::getKey).orElse(weight.keySet().iterator().next());
            current.merge(pick, -sum, Double::sum);
            return pick;
        }
    }

    static List<String> slots(Map<String, Double> gaps, int total) {
        SlotPicker picker = new SlotPicker(gaps);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            out.add(picker.next(java.util.Set.of()));
        }
        return out;
    }

    public static Result generate(PlanInputs in, LocalDate from, int days) {
        Integer dtt = daysToTest(in, from);
        Phase phase = phase(dtt);
        Map<String, Double> gaps = gaps(in);
        int budget = Math.max(30, Math.min(300, in.dailyMinutes()));
        LocalDate mockDay = mockDay(in, from, days, phase);

        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            dates.add(from.plusDays(i));
        }
        SlotPicker picker = new SlotPicker(gaps);
        Map<String, Integer> counters = new HashMap<>();
        int grammarIndex = 0;
        boolean diagnosticPlanned = in.diagnosticDone();
        List<Draft> tasks = new ArrayList<>();

        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = dates.get(i);
            Integer left = daysToTest(in, d);
            if (left != null && left < 0) {
                tasks.add(new Draft(d, PlanAction.VOCAB_REVIEW, null, "Keep your vocabulary fresh", "Your test date has passed — "
                        + "update it in Settings to get a new plan.", 15, 3));
                continue;
            }
            if (left != null && left == 0) {
                tasks.add(new Draft(d, PlanAction.REST, null, "Test day — good luck!", "No study today. Eat well, arrive early and "
                        + "bring the ID you registered with.", 0, 1));
                continue;
            }
            if (left != null && left == 1) {
                tasks.add(new Draft(d, PlanAction.VOCAB_REVIEW, null, "Light vocabulary review", "Only what is due — no new material "
                        + "the day before the test.", 10, 2));
                tasks.add(new Draft(d, PlanAction.REST, null, "Rest and prepare", "Check the venue, timings and ID; get a full night's "
                        + "sleep. Your preparation is done.", 0, 1));
                continue;
            }
            if (d.equals(mockDay)) {
                tasks.add(new Draft(d, PlanAction.MOCK_TEST, null, "Full mock test — all four papers", mockWhy(in, phase), 170, 1));
                tasks.add(new Draft(d, PlanAction.VOCAB_REVIEW, null, "Review due vocabulary", "Keep the daily habit, even on mock day.",
                        10, 3));
                continue;
            }

            boolean sunday = d.getDayOfWeek() == DayOfWeek.SUNDAY;
            int dayBudget = sunday ? (int) Math.round(budget * 0.6) : budget;
            List<Draft> day = new ArrayList<>();
            day.add(new Draft(d, PlanAction.VOCAB_REVIEW, null, "Review due vocabulary", i == 0 && in.vocabDue() > 0
                    ? in.vocabDue() + " cards are due — spaced repetition only works if you keep the streak."
                    : in.vocabTotal() == 0 ? "Your deck is empty — add a topic word bank first." : "Daily spaced-repetition review.", 15, 3));

            if (!diagnosticPlanned) {
                day.add(new Draft(d, PlanAction.GRAMMAR_DIAGNOSTIC, null, "Grammar diagnostic (40 questions)",
                        "Finds which of the 13 grammar areas are holding your Grammatical Range & Accuracy back.", 15, 1));
                diagnosticPlanned = true;
            } else if (i % 2 == 0 || sunday) {
                Draft g = grammarTask(in, d, grammarIndex++);
                if (g != null) {
                    day.add(g);
                }
            }
            if (sunday) {
                day.add(new Draft(d, PlanAction.REVIEW_MISTAKES, null, "Review this week's mistakes",
                        "Re-read the feedback on your graded work and the error log; rewrite two of your weakest sentences.", 20, 2));
            }

            int used = day.stream().mapToInt(Draft::minutes).sum();
            Map<String, Integer> today = new HashMap<>();
            int mains = 0;
            // Fill the day with practice until ~85% of the budget is used (at most four practice tasks, a skill at most twice).
            while (mains < 4 && (mains == 0 || used < dayBudget * 0.85)) {
                java.util.Set<String> full = new java.util.HashSet<>();
                today.forEach((m, n) -> {
                    if (n >= 2) {
                        full.add(m);
                    }
                });
                String module = picker.next(full);
                int k = counters.getOrDefault(module, 0);
                Draft main = mainTask(in, d, module, k, phase, gaps);
                if (mains > 0 && used + main.minutes() > dayBudget * 1.15) {
                    main = shorter(in, d, module, k, gaps);
                    if (main == null || used + main.minutes() > dayBudget * 1.15) {
                        break;
                    }
                }
                counters.merge(module, 1, Integer::sum);
                today.merge(module, 1, Integer::sum);
                used += main.minutes();
                day.add(main);
                mains++;
            }
            day.sort(Comparator.comparingInt(Draft::priority));
            tasks.addAll(day);
        }
        ensureCoverage(in, tasks, phase, gaps, counters);
        return new Result(phase, focus(in, phase, dtt, gaps, mockDay), tasks);
    }

    /** A short single-part alternative when a full task would overrun the day's budget. */
    private static Draft shorter(PlanInputs in, LocalDate d, String module, int k, Map<String, Double> gaps) {
        String why = why(in, module, gaps);
        return switch (module) {
            case "READING" -> readingPassage(d, k, why);
            case "LISTENING" -> listeningSection(d, k, why);
            case "SPEAKING" -> speakingPart(d, k, why);
            case "WRITING" -> new Draft(d, PlanAction.WRITING_TASK1, null, "Writing Task 1 — describe a figure in 20 minutes", why, 20, 1);
            default -> null;
        };
    }

    /** Every skill gets at least one task a week: a missing skill replaces a repeat of the most-scheduled one. */
    static void ensureCoverage(PlanInputs in, List<Draft> tasks, Phase phase, Map<String, Double> gaps, Map<String, Integer> counters) {
        for (String skill : SKILLS) {
            boolean present = tasks.stream().anyMatch(t -> t.action().module().equals(skill));
            if (present) {
                continue;
            }
            Map<String, Long> counts = tasks.stream().filter(t -> SKILLS.contains(t.action().module()))
                    .collect(Collectors.groupingBy(t -> t.action().module(), Collectors.counting()));
            String most = counts.entrySet().stream().filter(e -> e.getValue() > 1).max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            if (most == null) {
                continue;
            }
            for (int i = tasks.size() - 1; i >= 0; i--) {
                Draft t = tasks.get(i);
                if (t.action().module().equals(most)) {
                    int k = counters.merge(skill, 1, Integer::sum) - 1;
                    Draft replacement = shorter(in, t.date(), skill, k, gaps);
                    tasks.set(i, replacement == null ? mainTask(in, t.date(), skill, k, phase, gaps) : replacement);
                    break;
                }
            }
        }
    }

    private static boolean isNormalDay(PlanInputs in, LocalDate d, LocalDate mockDay) {
        Integer left = daysToTest(in, d);
        return (left == null || left > 1) && !d.equals(mockDay);
    }

    /** One full mock per week once the test is within five weeks (a baseline first if none has been taken). */
    static LocalDate mockDay(PlanInputs in, LocalDate from, int days, Phase phase) {
        Integer dtt = daysToTest(in, from);
        if (dtt == null || dtt < 3 || phase == Phase.AFTER) {
            return null;
        }
        if (in.daysSinceMock() != null && in.daysSinceMock() < 5) {
            return null;
        }
        if (phase == Phase.BUILD && dtt > 35) {
            return null;
        }
        if (phase == Phase.EXAM_WEEK) {
            LocalDate d = in.testDate().minusDays(Math.min(5, dtt - 2));
            return d.isBefore(from) ? null : d;
        }
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY && ChronoUnit.DAYS.between(d, in.testDate()) >= 2) {
                return d;
            }
        }
        return null;
    }

    private static String mockWhy(PlanInputs in, Phase phase) {
        if (in.daysSinceMock() == null) {
            return "Your first full mock: real timings back to back give a reliable baseline band for every skill.";
        }
        return phase == Phase.EXAM_WEEK ? "Final dress rehearsal — same order and timings as the real test."
                : "Weekly full test under exam conditions to track your overall band.";
    }

    static Draft grammarTask(PlanInputs in, LocalDate d, int index) {
        List<Draft> options = new ArrayList<>();
        for (PlanInputs.ErrorFocus e : in.topErrors()) {
            options.add(new Draft(d, PlanAction.GRAMMAR_ERRORS, e.subtype(), "Error drill: " + humanise(e.subtype()),
                    "Seen " + e.total() + "× in your graded writing and speaking" + trendNote(e.trend()) + ".", 15, 2));
        }
        for (PlanInputs.AreaFocus a : in.weakGrammarAreas()) {
            options.add(new Draft(d, PlanAction.GRAMMAR_AREA, a.key(), "Grammar: " + a.name(), a.proficiency() == null
                    ? "Not practised yet." : "Diagnostic proficiency " + Math.round(a.proficiency()) + "% — one of your weakest areas.",
                    15, 2));
        }
        return options.isEmpty() ? null : options.get(index % options.size());
    }

    private static String trendNote(String trend) {
        if (trend == null) {
            return "";
        }
        return switch (trend) {
            case "WORSENING" -> " and getting more frequent";
            case "NEW" -> ", first seen recently";
            case "IMPROVING" -> " — improving, keep it up";
            default -> "";
        };
    }

    static Draft mainTask(PlanInputs in, LocalDate d, String module, int k, Phase phase, Map<String, Double> gaps) {
        boolean exam = phase == Phase.SHARPEN || phase == Phase.EXAM_WEEK;
        String why = why(in, module, gaps);
        return switch (module) {
            case "READING" -> exam && k % 2 == 0
                    ? new Draft(d, PlanAction.READING_TEST, null, "Full Reading test — 60 minutes, exam mode", why, 60, 1)
                    : readingPassage(d, k, why);
            case "LISTENING" -> exam && k % 2 == 0
                    ? new Draft(d, PlanAction.LISTENING_TEST, null, "Full Listening test — 4 parts, played once", why, 40, 1)
                    : listeningSection(d, k, why);
            case "WRITING" -> exam && k % 2 == 0
                    ? new Draft(d, PlanAction.WRITING_TEST, null, "Writing test — Task 1 and Task 2 in 60 minutes", why, 60, 1)
                    : k % 3 == 1
                            ? new Draft(d, PlanAction.WRITING_TASK1, null, "Writing Task 1 — describe a figure in 20 minutes", why, 20, 1)
                            : new Draft(d, PlanAction.WRITING_TASK2, null, "Writing Task 2 essay — 40 minutes", why, 40, 1);
            case "SPEAKING" -> exam && k % 2 == 0
                    ? new Draft(d, PlanAction.SPEAKING_TEST, null, "Full Speaking test with the AI examiner", why, 15, 1)
                    : speakingPart(d, k, why);
            default -> throw new IllegalArgumentException(module);
        };
    }

    private static Draft readingPassage(LocalDate d, int k, String why) {
        String p = List.of("3", "2", "3", "1").get(k % 4);
        return new Draft(d, PlanAction.READING_PASSAGE, p, "Reading Passage " + p + " — 20 minutes", why, 20, 1);
    }

    private static Draft listeningSection(LocalDate d, int k, String why) {
        String s = List.of("4", "3", "2", "4", "1").get(k % 5);
        return new Draft(d, PlanAction.LISTENING_SECTION, s, "Listening Part " + s, why, 10, 1);
    }

    private static Draft speakingPart(LocalDate d, int k, String why) {
        String p = List.of("PART2", "PART3", "PART1").get(k % 3);
        String title = switch (p) {
            case "PART1" -> "Speaking Part 1 — interview questions";
            case "PART2" -> "Speaking Part 2 — 2-minute long turn";
            default -> "Speaking Part 3 — discussion";
        };
        return new Draft(d, PlanAction.SPEAKING_PART, p, title, why, 10, 1);
    }

    static String why(PlanInputs in, String module, Map<String, Double> gaps) {
        Double band = in.bands().get(module);
        String name = humanise(module);
        if (module.equals("READING") || module.equals("LISTENING")) {
            PlanInputs.WeakSpot weak = in.weakQuestionTypes().stream().filter(w -> w.module().equals(module)).findFirst().orElse(null);
            if (weak != null) {
                return "Weakest " + name + " question type: " + humanise(weak.questionType()) + " (" + Math.round(weak.accuracy() * 100)
                        + "% correct).";
            }
        }
        Map<String, Double> criteria = module.equals("WRITING") ? in.writingCriteria() : module.equals("SPEAKING") ? in.speakingCriteria() : Map.of();
        if (criteria != null && !criteria.isEmpty()) {
            Map.Entry<String, Double> low = criteria.entrySet().stream().filter(e -> e.getValue() > 0)
                    .min(Map.Entry.comparingByValue()).orElse(null);
            if (low != null) {
                return "Lowest criterion lately: " + humanise(low.getKey()) + " (" + String.format(Locale.ROOT, "%.1f", low.getValue()) + ").";
            }
        }
        if (band == null) {
            return "No " + name + " result yet — this gives you a first band estimate.";
        }
        return name + " is at " + String.format(Locale.ROOT, "%.1f", band) + " against your " + String.format(Locale.ROOT, "%.1f", in.targetBand())
                + " target" + (gaps.get(module) >= 1 ? " — one of your biggest gaps." : ".");
    }

    static String focus(PlanInputs in, Phase phase, Integer dtt, Map<String, Double> gaps, LocalDate mockDay) {
        List<String> top = byGap(gaps).stream().limit(2).map(m -> {
            Double b = in.bands().get(m);
            return humanise(m) + (b == null ? " (not measured yet)" : " (" + String.format(Locale.ROOT, "%.1f", b) + " → "
                    + String.format(Locale.ROOT, "%.1f", in.targetBand()) + ")");
        }).collect(Collectors.toList());
        StringBuilder sb = new StringBuilder(phase.label()).append(" phase");
        if (dtt != null && dtt >= 0) {
            sb.append(" — ").append(dtt).append(dtt == 1 ? " day" : " days").append(" to go");
        }
        sb.append(". Most practice time goes to ").append(String.join(" and ", top)).append(". ");
        sb.append(switch (phase) {
            case BUILD -> "Short, focused practice on single passages, parts and tasks builds accuracy before speed.";
            case SHARPEN -> "Switch to timed full papers in exam mode and fix the errors that keep recurring.";
            case EXAM_WEEK -> "Keep sessions short and timed, avoid new material late in the week and rest before the test.";
            case AFTER -> "Update your test date in Settings to plan the next attempt.";
        });
        if (mockDay != null) {
            sb.append(" Full mock test on ").append(mockDay.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.UK)).append('.');
        }
        return sb.toString();
    }

    static String humanise(String key) {
        String s = key.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
