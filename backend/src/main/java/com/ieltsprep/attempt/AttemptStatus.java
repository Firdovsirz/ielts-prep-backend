package com.ieltsprep.attempt;

public enum AttemptStatus {
    /** Auto-marked (Reading, Listening, closed grammar items). */
    MARKED,
    /** Submitted and waiting for Claude grading. */
    GRADING,
    GRADED,
    /** Grading failed (no API key, spend cap, API error) — can be retried. */
    GRADING_FAILED,
    /** Self-assessed by the candidate (no API key available). */
    SELF_ASSESSED
}
