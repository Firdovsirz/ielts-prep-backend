package com.ieltsprep.claude;

import java.util.List;
import java.util.Map;

/**
 * One structured-output call: which prompt file, template variables, the record to deserialise into, and
 * bookkeeping (a reference for the usage log, whether it is a background job, optional prior conversation turns).
 */
public record ClaudeCall<T>(
        String prompt,
        Map<String, ?> vars,
        Class<T> type,
        String ref,
        boolean background,
        List<Turn> history) {

    /** A prior conversation turn (examiner mode). role is "user" or "assistant". */
    public record Turn(String role, String text) {}

    public static <T> ClaudeCall<T> of(String prompt, Map<String, ?> vars, Class<T> type) {
        return new ClaudeCall<>(prompt, vars, type, null, false, List.of());
    }

    public ClaudeCall<T> ref(String ref) {
        return new ClaudeCall<>(prompt, vars, type, ref, background, history);
    }

    /** Marks the call as a background job (limited to claude.background-share of the daily cap). */
    public ClaudeCall<T> inBackground() {
        return new ClaudeCall<>(prompt, vars, type, ref, true, history);
    }

    public ClaudeCall<T> history(List<Turn> turns) {
        return new ClaudeCall<>(prompt, vars, type, ref, background, List.copyOf(turns));
    }
}
