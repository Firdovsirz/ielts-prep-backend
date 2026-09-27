package com.ieltsprep.common;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Essay/transcript word counting — identical to the frontend editor's live counter (src/lib/wordCount.ts). */
public final class TextStats {

    private static final Pattern HAS_WORD_CHAR = Pattern.compile("[\\p{L}\\p{N}]");

    private TextStats() {}

    public static int words(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(text.trim().split("\\s+")).filter(t -> HAS_WORD_CHAR.matcher(t).find()).count();
    }
}
