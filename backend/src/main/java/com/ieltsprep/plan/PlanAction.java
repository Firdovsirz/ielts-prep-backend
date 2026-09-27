package com.ieltsprep.plan;

import com.ieltsprep.session.SessionKind;
import java.util.Set;

/** What a study-plan task asks the candidate to do; the frontend maps each action to a start button. */
public enum PlanAction {
    READING_PASSAGE("READING", 20, Set.of(SessionKind.READING_PASSAGE)),
    READING_TEST("READING", 60, Set.of(SessionKind.READING_TEST)),
    LISTENING_SECTION("LISTENING", 10, Set.of(SessionKind.LISTENING_SECTION)),
    LISTENING_TEST("LISTENING", 40, Set.of(SessionKind.LISTENING_TEST)),
    WRITING_TASK1("WRITING", 20, Set.of(SessionKind.WRITING_TASK1, SessionKind.WRITING_TEST)),
    WRITING_TASK2("WRITING", 40, Set.of(SessionKind.WRITING_TASK2, SessionKind.WRITING_TEST)),
    WRITING_TEST("WRITING", 60, Set.of(SessionKind.WRITING_TEST)),
    SPEAKING_PART("SPEAKING", 10, Set.of(SessionKind.SPEAKING_PART, SessionKind.SPEAKING_TEST)),
    SPEAKING_TEST("SPEAKING", 15, Set.of(SessionKind.SPEAKING_TEST)),
    GRAMMAR_DIAGNOSTIC("GRAMMAR", 15, Set.of(SessionKind.GRAMMAR_DIAGNOSTIC)),
    GRAMMAR_AREA("GRAMMAR", 15, Set.of(SessionKind.GRAMMAR_EXERCISE, SessionKind.GRAMMAR_ERROR_DRILL)),
    GRAMMAR_ERRORS("GRAMMAR", 15, Set.of(SessionKind.GRAMMAR_ERROR_DRILL, SessionKind.GRAMMAR_EXERCISE)),
    VOCAB_REVIEW("VOCAB", 15, Set.of(SessionKind.VOCAB_REVIEW)),
    MOCK_TEST("MOCK", 170, Set.of()),
    REVIEW_MISTAKES("GENERAL", 20, Set.of()),
    REST("GENERAL", 0, Set.of());

    private final String module;
    private final int defaultMinutes;
    private final Set<SessionKind> completedBy;

    PlanAction(String module, int defaultMinutes, Set<SessionKind> completedBy) {
        this.module = module;
        this.defaultMinutes = defaultMinutes;
        this.completedBy = completedBy;
    }

    public String module() {
        return module;
    }

    public int defaultMinutes() {
        return defaultMinutes;
    }

    public Set<SessionKind> completedBy() {
        return completedBy;
    }
}
