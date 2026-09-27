package com.ieltsprep.grammar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure logic of the 40-question adaptive grammar diagnostic. Each of the 13 areas gets three questions: a level-2
 * (band 6.5–7) opener; a level-3 question after a correct answer or a level-1 question after a wrong one; then a
 * third question at the level the first two point to. The 40th question goes to the least certain area.
 * Bank ids follow "&lt;AREA&gt;-&lt;level&gt;&lt;a|b&gt;" (2 per area and level).
 */
public final class AdaptiveDiagnostic {

    public static final int LENGTH = 40;

    public record Answer(String questionId, String area, int level, boolean correct) {}

    public record State(List<String> areas, List<Answer> answers) {
        public int asked() {
            return answers.size();
        }

        public boolean finished() {
            return answers.size() >= LENGTH;
        }
    }

    private AdaptiveDiagnostic() {}

    public static State start(List<String> areas) {
        return new State(List.copyOf(areas), List.of());
    }

    public static State record(State state, Answer answer) {
        List<Answer> next = new ArrayList<>(state.answers());
        next.add(answer);
        return new State(state.areas(), List.copyOf(next));
    }

    /** Id of the next question to ask, given the bank's available ids, or empty when finished. */
    public static Optional<String> next(State state, Set<String> bank) {
        if (state.finished()) {
            return Optional.empty();
        }
        Map<String, List<Answer>> byArea = byArea(state);
        Set<String> used = state.answers().stream().map(Answer::questionId).collect(Collectors.toSet());
        int round = state.asked() / state.areas().size();
        if (round < 3) {
            String area = state.areas().get(state.asked() % state.areas().size());
            List<Answer> history = byArea.getOrDefault(area, List.of());
            int level = switch (history.size()) {
                case 0 -> 2;
                case 1 -> history.getFirst().correct() ? 3 : 1;
                default -> {
                    boolean a = history.get(0).correct();
                    boolean b = history.get(1).correct();
                    yield a && b ? 3 : !a && !b ? 1 : 2;
                }
            };
            return pick(area, level, used, bank);
        }
        // tie-break question: the area whose answers disagree most, at the middle level
        String area = state.areas().stream()
                .max(Comparator.comparingDouble((String a) -> uncertainty(byArea.getOrDefault(a, List.of()))))
                .orElseThrow();
        return pick(area, 2, used, bank).or(() -> pick(area, 1, used, bank)).or(() -> pick(area, 3, used, bank));
    }

    private static Optional<String> pick(String area, int level, Set<String> used, Set<String> bank) {
        for (int l : new int[] {level, level == 3 ? 2 : level + 1, level == 1 ? 2 : level - 1}) {
            for (String v : new String[] {"a", "b"}) {
                String id = area + "-" + l + v;
                if (bank.contains(id) && !used.contains(id)) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    private static double uncertainty(List<Answer> answers) {
        if (answers.isEmpty()) {
            return 1;
        }
        double p = answers.stream().filter(Answer::correct).count() / (double) answers.size();
        return p * (1 - p);
    }

    /**
     * Proficiency 0–100 per area: half the level-weighted accuracy, half the highest level answered correctly.
     * e.g. L2✓ L3✓ L3✓ = 100; L2✓ L3✓ L3✗ = 81; L2✓ L3✗ L2✓ = 62; L2✗ L1✓ L1✓ = 42; all wrong = 0.
     */
    public static Map<String, Double> scores(State state) {
        Map<String, Double> out = new LinkedHashMap<>();
        byArea(state).forEach((area, answers) -> {
            double asked = answers.stream().mapToInt(Answer::level).sum();
            double earned = answers.stream().filter(Answer::correct).mapToInt(Answer::level).sum();
            int highest = answers.stream().filter(Answer::correct).mapToInt(Answer::level).max().orElse(0);
            double score = 0.5 * (asked == 0 ? 0 : earned / asked * 100) + 0.5 * (highest / 3.0 * 100);
            out.put(area, Math.round(score * 10) / 10.0);
        });
        return out;
    }

    private static Map<String, List<Answer>> byArea(State state) {
        Map<String, List<Answer>> byArea = new LinkedHashMap<>();
        state.areas().forEach(a -> byArea.put(a, new ArrayList<>()));
        state.answers().forEach(a -> byArea.computeIfAbsent(a.area(), k -> new ArrayList<>()).add(a));
        return byArea;
    }
}
