package com.ieltsprep.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class Sm2Test {

    static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    static final Sm2.State NEW = new Sm2.State(2.5, 0, 0, 0);

    @Test
    void goodAnswersFollowTwoSixThenEaseSchedule() {
        Sm2.Outcome first = Sm2.review(NEW, 4, NOW);
        assertThat(first.state().intervalDays()).isEqualTo(2);
        assertThat(first.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(2)));
        assertThat(first.state().easeFactor()).isEqualTo(2.5);

        Sm2.Outcome second = Sm2.review(first.state(), 4, NOW);
        assertThat(second.state().intervalDays()).isEqualTo(6);
        Sm2.Outcome third = Sm2.review(second.state(), 4, NOW);
        assertThat(third.state().intervalDays()).isEqualTo(15); // 6 × 2.5
        assertThat(third.state().repetitions()).isEqualTo(3);
    }

    @Test
    void againResetsRepetitionsRecordsALapseAndRelearnsInTenMinutes() {
        Sm2.State learned = new Sm2.State(2.5, 15, 3, 0);
        Sm2.Outcome o = Sm2.review(learned, 1, NOW);
        assertThat(o.state().repetitions()).isZero();
        assertThat(o.state().intervalDays()).isZero();
        assertThat(o.state().lapses()).isEqualTo(1);
        assertThat(o.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(o.state().easeFactor()).isCloseTo(1.96, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void easeNeverFallsBelowTheFloorAndEasyGetsABonus() {
        Sm2.State s = NEW;
        for (int i = 0; i < 10; i++) {
            s = Sm2.review(s, 0, NOW).state();
        }
        assertThat(s.easeFactor()).isEqualTo(1.3);

        Sm2.State two = new Sm2.State(2.5, 6, 2, 0);
        int good = Sm2.review(two, 4, NOW).state().intervalDays();
        int easy = Sm2.review(two, 5, NOW).state().intervalDays();
        assertThat(easy).isGreaterThan(good);
        assertThat(Sm2.review(two, 3, NOW).state().easeFactor()).isLessThan(2.5);
    }

    @Test
    void theFourButtonsAlwaysGiveIncreasingIntervals() {
        for (Sm2.State s : new Sm2.State[] {NEW, new Sm2.State(2.5, 2, 1, 0), new Sm2.State(1.3, 3, 4, 2), new Sm2.State(2.8, 40, 6, 0)}) {
            int hard = Sm2.review(s, 3, NOW).state().intervalDays();
            int good = Sm2.review(s, 4, NOW).state().intervalDays();
            int easy = Sm2.review(s, 5, NOW).state().intervalDays();
            assertThat(hard).as("hard %s", s).isLessThan(good);
            assertThat(good).as("good %s", s).isLessThan(easy);
            if (s.repetitions() > 0) {
                assertThat(hard).as("hard grows %s", s).isGreaterThan(s.intervalDays());
            }
        }
    }

    @Test
    void previewsAreHumanReadable() {
        assertThat(Sm2.preview(NEW, 1, NOW)).isEqualTo("10 min");
        assertThat(Sm2.preview(NEW, 3, NOW)).isEqualTo("1 d");
        assertThat(Sm2.preview(NEW, 4, NOW)).isEqualTo("2 d");
        assertThat(Sm2.preview(NEW, 5, NOW)).isEqualTo("4 d");
        assertThat(Sm2.preview(new Sm2.State(2.5, 60, 5, 0), 4, NOW)).isEqualTo("5 mo");
    }
}
