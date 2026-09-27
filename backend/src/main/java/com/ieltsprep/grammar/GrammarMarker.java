package com.ieltsprep.grammar;

import java.util.List;
import java.util.Locale;

/** Exact-match marking for closed grammar items (gap-fills, corrections with a known answer). */
public final class GrammarMarker {

    private GrammarMarker() {}

    public static boolean matches(String given, List<String> accepted) {
        if (given == null || given.isBlank() || accepted == null) {
            return false;
        }
        String g = normalise(given);
        return accepted.stream().map(GrammarMarker::normalise).anyMatch(g::equals);
    }

    static String normalise(String s) {
        String t = s.toLowerCase(Locale.ROOT)
                .replaceAll("[\\u2018\\u2019`´]", "'")
                .replaceAll("[\\u201C\\u201D]", "\"")
                .replaceAll("[\\u2013\\u2014]", "-")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.;:!?])", "$1")
                .trim();
        // a trailing full stop is optional; "zero/no article" answers are written as "-"
        t = t.replaceAll("[.!]+$", "").trim();
        if (t.equals("no article") || t.equals("zero") || t.equals("zero article") || t.equals("—") || t.equals("–")) {
            t = "-";
        }
        return t;
    }
}
