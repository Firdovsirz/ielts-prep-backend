package com.ieltsprep.common;

import java.util.Locale;

/** Finds the sentence of a text that contains an excerpt (used to show candidates their own sentence in drills). */
public final class Sentences {

    private Sentences() {}

    public static String containing(String text, String excerpt) {
        if (text == null || excerpt == null || excerpt.isBlank()) {
            return excerpt == null ? "" : excerpt;
        }
        String needle = excerpt.trim().toLowerCase(Locale.ROOT);
        for (String sentence : text.split("(?<=[.!?])\\s+|\\n+")) {
            if (sentence.toLowerCase(Locale.ROOT).contains(needle)) {
                return sentence.trim();
            }
        }
        return excerpt.trim();
    }
}
