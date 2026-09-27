package com.ieltsprep.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.ieltsprep.plan.PlanGenerator.Draft;
import com.ieltsprep.plan.PlanGenerator.Phase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlanGeneratorTest {

    static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
    static final LocalDate TEST = LocalDate.of(2026, 10, 27);

    static PlanInputs inputs(LocalDate today, Map<String, Double> bands, boolean diagnosticDone, Integer daysSinceMock) {
        return new PlanInputs(today, TEST, 8.0, 6.5, bands, 90,
                List.of(new PlanInputs.WeakSpot("READING", "MATCHING_HEADINGS", 0.45, 11)),
                List.of(new PlanInputs.ErrorFocus("article_missing", "articles", 7, "WORSENING")),
                List.of(new PlanInputs.AreaFocus("articles", "Articles and determiners", 48.0)),
                diagnosticDone, 12, 40, Map.of(), daysSinceMock, Map.of("LEXICAL_RESOURCE", 6.0, "TASK_RESPONSE", 7.0), Map.of(), List.of());
    }

    static Map<String, Double> bands(Double l, Double r, Double w, Double s) {
        Map<String, Double> m = new HashMap<>();
        m.put("LISTENING", l);
        m.put("READING", r);
        m.put("WRITING", w);
        m.put("SPEAKING", s);
        return m;
    }

    @Test
    void phasesTightenAsTheTestApproaches() {
        assertThat(PlanGenerator.phase(null)).isEqualTo(Phase.BUILD);
        assertThat(PlanGenerator.phase(29)).isEqualTo(Phase.BUILD);
        assertThat(PlanGenerator.phase(21)).isEqualTo(Phase.SHARPEN);
        assertThat(PlanGenerator.phase(7)).isEqualTo(Phase.EXAM_WEEK);
        assertThat(PlanGenerator.phase(0)).isEqualTo(Phase.EXAM_WEEK);
        assertThat(PlanGenerator.phase(-1)).isEqualTo(Phase.AFTER);
    }

    @Test
    void buildWeekFavoursTheWeakestSkillAndKeepsDailyHabits() {
        var result = PlanGenerator.generate(inputs(MONDAY, bands(7.0, 7.5, 6.0, 6.5), false, null), MONDAY, 7);
        assertThat(result.phase()).isEqualTo(Phase.BUILD);
        Map<LocalDate, List<Draft>> byDay = result.tasks().stream().collect(Collectors.groupingBy(Draft::date));
        assertThat(byDay).hasSize(7);
        byDay.values().forEach(day -> assertThat(day.stream().filter(t -> t.action() == PlanAction.VOCAB_REVIEW)).hasSize(1));

        assertThat(byDay.get(MONDAY)).extracting(Draft::action).contains(PlanAction.GRAMMAR_DIAGNOSTIC);
        LocalDate saturday = MONDAY.plusDays(5);
        assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(byDay.get(saturday)).extracting(Draft::action).contains(PlanAction.MOCK_TEST);
        assertThat(result.focus()).contains("Build phase").contains("Writing (6.0 → 8.0)").contains("Saturday");

        Map<String, Long> perSkill = result.tasks().stream().filter(t -> PlanGenerator.SKILLS.contains(t.action().module()))
                .collect(Collectors.groupingBy(t -> t.action().module(), Collectors.counting()));
        assertThat(perSkill.keySet()).containsExactlyInAnyOrderElementsOf(PlanGenerator.SKILLS);
        assertThat(perSkill.get("WRITING")).isGreaterThanOrEqualTo(perSkill.get("READING"));
        assertThat(perSkill.get("WRITING")).isGreaterThan(perSkill.get("LISTENING"));

        byDay.forEach((d, day) -> {
            if (!d.equals(saturday)) {
                assertThat(day.stream().mapToInt(Draft::minutes).sum()).as("minutes on %s", d).isLessThanOrEqualTo((int) (90 * 1.15) + 15);
            }
        });
        assertThat(result.tasks()).filteredOn(t -> t.action() == PlanAction.READING_PASSAGE)
                .allSatisfy(t -> assertThat(t.why()).contains("Matching headings"));
        assertThat(result.tasks()).filteredOn(t -> t.action() == PlanAction.GRAMMAR_ERRORS)
                .allSatisfy(t -> assertThat(t.why()).contains("7×").contains("more frequent"));
    }

    @Test
    void examWeekHasADressRehearsalAndRestsBeforeTheTest() {
        LocalDate today = TEST.minusDays(5);
        var result = PlanGenerator.generate(inputs(today, bands(7.5, 7.5, 7.0, 7.0), true, 9), today, 7);
        assertThat(result.phase()).isEqualTo(Phase.EXAM_WEEK);
        Map<LocalDate, List<Draft>> byDay = result.tasks().stream().collect(Collectors.groupingBy(Draft::date));
        assertThat(byDay.get(TEST)).extracting(Draft::action).containsExactly(PlanAction.REST);
        assertThat(byDay.get(TEST.minusDays(1))).extracting(Draft::action).containsExactlyInAnyOrder(PlanAction.VOCAB_REVIEW, PlanAction.REST);
        assertThat(byDay.get(TEST.minusDays(3))).extracting(Draft::action).contains(PlanAction.MOCK_TEST);
        assertThat(byDay.get(TEST.plusDays(1))).extracting(Draft::action).containsExactly(PlanAction.VOCAB_REVIEW);
    }

    @Test
    void sharpenPhaseUsesTimedFullPapersAndSkipsARecentMock() {
        LocalDate today = TEST.minusDays(14);
        var result = PlanGenerator.generate(inputs(today, bands(7.0, 7.0, 6.5, 6.5), true, 2), today, 7);
        assertThat(result.phase()).isEqualTo(Phase.SHARPEN);
        assertThat(result.tasks()).extracting(Draft::action).doesNotContain(PlanAction.MOCK_TEST, PlanAction.GRAMMAR_DIAGNOSTIC)
                .containsAnyOf(PlanAction.READING_TEST, PlanAction.LISTENING_TEST, PlanAction.WRITING_TEST, PlanAction.SPEAKING_TEST);
    }

    @Test
    void unmeasuredSkillsAreScheduledEarlyAndExplained() {
        var result = PlanGenerator.generate(inputs(MONDAY, bands(null, 7.0, 6.5, null), true, null), MONDAY, 7);
        assertThat(result.tasks()).filteredOn(t -> t.action().module().equals("SPEAKING"))
                .isNotEmpty().allSatisfy(t -> assertThat(t.why()).contains("No Speaking result yet"));
    }

    @Test
    void oneEmptyAttemptDoesNotCrowdOutTheOtherSkillsAndDaysUseTheBudget() {
        var result = PlanGenerator.generate(inputs(MONDAY, bands(0.0, 7.0, null, 6.5), true, 3), MONDAY, 7);
        List<Draft> practice = result.tasks().stream().filter(t -> PlanGenerator.SKILLS.contains(t.action().module())).toList();
        long listening = practice.stream().filter(t -> t.action().module().equals("LISTENING")).count();
        assertThat(listening).isLessThanOrEqualTo(practice.size() / 2);
        assertThat(practice.stream().map(t -> t.action().module()).distinct()).hasSize(4);
        result.tasks().stream().collect(Collectors.groupingBy(Draft::date)).forEach((d, day) -> {
            int minutes = day.stream().mapToInt(Draft::minutes).sum();
            int budget = d.getDayOfWeek() == DayOfWeek.SUNDAY ? 54 : 90;
            assertThat(minutes).as("minutes on %s", d).isBetween((int) (budget * 0.7), (int) (budget * 1.15) + 15);
        });
    }

    @Test
    void weightedSlotsCoverEverySkill() {
        Map<String, Double> gaps = Map.of("LISTENING", 0.5, "READING", 0.0, "WRITING", 2.0, "SPEAKING", 1.0);
        List<String> slots = PlanGenerator.slots(gaps, 12);
        Map<String, Long> counts = slots.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        assertThat(counts.keySet()).containsExactlyInAnyOrder("LISTENING", "READING", "WRITING", "SPEAKING");
        assertThat(counts.get("WRITING")).isGreaterThan(counts.get("SPEAKING"));
        assertThat(counts.get("SPEAKING")).isGreaterThan(counts.get("READING"));
        assertThat(slots.getFirst()).isEqualTo("WRITING");
    }
}
