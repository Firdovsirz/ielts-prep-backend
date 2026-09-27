package com.ieltsprep.vocab;

import java.time.Duration;
import java.time.Instant;

/**
 * SM-2 spaced repetition (SuperMemo 2, Wozniak 1990) with Anki-style button spacing. Grades 0–5; the app's buttons map
 * to Again=1, Hard=3, Good=4, Easy=5. The ease factor follows SM-2 exactly. Intervals:
 * <ul>
 *   <li>Again (grade &lt; 3): a lapse — relearn in 10 minutes and start the repetitions again;</li>
 *   <li>first repetition: Hard 1 d, Good 2 d, Easy 4 d;</li>
 *   <li>second repetition: Hard 1.2 × previous, Good 6 d, Easy 8 d;</li>
 *   <li>then: Hard 1.2 × previous, Good previous × ease, Easy previous × ease × 1.3 — each strictly longer than the
 *       button before it, so the four choices always differ.</li>
 * </ul>
 */
public final class Sm2 {

    public record State(double easeFactor, int intervalDays, int repetitions, int lapses) {}

    public record Outcome(State state, Instant dueAt) {}

    public static final Duration RELEARN = Duration.ofMinutes(10);

    private Sm2() {}

    public static Outcome review(State s, int grade, Instant now) {
        int q = Math.max(0, Math.min(5, grade));
        double ef = Math.max(1.3, s.easeFactor() + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)));
        if (q < 3) {
            State next = new State(ef, 0, 0, s.lapses() + 1);
            return new Outcome(next, now.plus(RELEARN));
        }
        int reps = s.repetitions() + 1;
        int prev = Math.max(1, s.intervalDays());
        int hard = reps == 1 ? 1 : Math.max(prev + 1, (int) Math.round(prev * 1.2));
        int good = reps == 1 ? 2 : reps == 2 ? Math.max(6, hard + 1) : Math.max(hard + 1, (int) Math.round(prev * ef));
        int easy = reps == 1 ? 4 : reps == 2 ? Math.max(8, good + 1) : Math.max(good + 1, (int) Math.round(prev * ef * 1.3));
        int interval = q == 3 ? hard : q == 4 ? good : easy;
        return new Outcome(new State(ef, interval, reps, s.lapses()), now.plus(Duration.ofDays(interval)));
    }

    /** Human-readable next interval for each button, shown before answering. */
    public static String preview(State s, int grade, Instant now) {
        Duration d = Duration.between(now, review(s, grade, now).dueAt());
        if (d.toMinutes() < 60) {
            return d.toMinutes() + " min";
        }
        long days = d.toDays();
        return days < 31 ? days + " d" : Math.round(days / 30.0) + " mo";
    }
}
