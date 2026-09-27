package com.ieltsprep.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import com.ieltsprep.grammar.AdaptiveDiagnostic.Answer;
import com.ieltsprep.grammar.AdaptiveDiagnostic.State;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class AdaptiveDiagnosticTest {

    static final List<String> AREAS = List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "A12", "A13");

    static Set<String> bank() {
        Set<String> ids = new HashSet<>();
        for (String a : AREAS) {
            for (int l = 1; l <= 3; l++) {
                ids.add(a + "-" + l + "a");
                ids.add(a + "-" + l + "b");
            }
        }
        return ids;
    }

    /** Runs a full diagnostic where {@code knows} decides whether each question is answered correctly. */
    static State run(Predicate<String> knows) {
        State s = AdaptiveDiagnostic.start(AREAS);
        Set<String> bank = bank();
        while (true) {
            var next = AdaptiveDiagnostic.next(s, bank);
            if (next.isEmpty()) {
                return s;
            }
            String id = next.get();
            String area = id.substring(0, id.lastIndexOf('-'));
            int level = Character.getNumericValue(id.charAt(id.lastIndexOf('-') + 1));
            s = AdaptiveDiagnostic.record(s, new Answer(id, area, level, knows.test(id)));
        }
    }

    @Test
    void asksFortyQuestionsWithoutRepeats() {
        State s = run(id -> true);
        assertThat(s.asked()).isEqualTo(40);
        assertThat(s.answers().stream().map(Answer::questionId).distinct().count()).isEqualTo(40);
        // every area gets three questions (plus one tie-break)
        AREAS.forEach(a -> assertThat(s.answers().stream().filter(x -> x.area().equals(a)).count()).isBetween(3L, 4L));
    }

    @Test
    void goesHarderAfterCorrectAndEasierAfterWrong() {
        State strong = run(id -> true);
        assertThat(strong.answers().stream().filter(a -> a.area().equals("A1")).map(Answer::level).toList()).startsWith(2, 3, 3);
        State weak = run(id -> false);
        assertThat(weak.answers().stream().filter(a -> a.area().equals("A1")).map(Answer::level).toList()).startsWith(2, 1, 1);
    }

    @Test
    void scoresReflectLevelReached() {
        State s = AdaptiveDiagnostic.start(List.of("X", "Y", "Z", "W", "V"));
        s = AdaptiveDiagnostic.record(s, new Answer("X-2a", "X", 2, true));
        s = AdaptiveDiagnostic.record(s, new Answer("X-3a", "X", 3, true));
        s = AdaptiveDiagnostic.record(s, new Answer("X-3b", "X", 3, true));
        s = AdaptiveDiagnostic.record(s, new Answer("Y-2a", "Y", 2, true));
        s = AdaptiveDiagnostic.record(s, new Answer("Y-3a", "Y", 3, true));
        s = AdaptiveDiagnostic.record(s, new Answer("Y-3b", "Y", 3, false));
        s = AdaptiveDiagnostic.record(s, new Answer("Z-2a", "Z", 2, true));
        s = AdaptiveDiagnostic.record(s, new Answer("Z-3a", "Z", 3, false));
        s = AdaptiveDiagnostic.record(s, new Answer("Z-2b", "Z", 2, true));
        s = AdaptiveDiagnostic.record(s, new Answer("W-2a", "W", 2, false));
        s = AdaptiveDiagnostic.record(s, new Answer("W-1a", "W", 1, true));
        s = AdaptiveDiagnostic.record(s, new Answer("W-1b", "W", 1, true));
        s = AdaptiveDiagnostic.record(s, new Answer("V-2a", "V", 2, false));
        s = AdaptiveDiagnostic.record(s, new Answer("V-1a", "V", 1, false));
        s = AdaptiveDiagnostic.record(s, new Answer("V-1b", "V", 1, false));
        var scores = AdaptiveDiagnostic.scores(s);
        assertThat(scores.get("X")).isEqualTo(100.0);
        assertThat(scores.get("Y")).isEqualTo(81.3);
        assertThat(scores.get("Z")).isEqualTo(61.9);
        assertThat(scores.get("W")).isEqualTo(41.7);
        assertThat(scores.get("V")).isEqualTo(0.0);
    }
}
