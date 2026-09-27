package com.ieltsprep.marking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts written numbers ("twenty-five", "one hundred and fifty", "fifteenth") into digits. */
final class NumberWords {

    private NumberWords() {}

    private static final Map<String, Integer> UNITS = Map.ofEntries(
            Map.entry("zero", 0), Map.entry("nought", 0), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
            Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12),
            Map.entry("thirteen", 13), Map.entry("fourteen", 14), Map.entry("fifteen", 15), Map.entry("sixteen", 16),
            Map.entry("seventeen", 17), Map.entry("eighteen", 18), Map.entry("nineteen", 19), Map.entry("twenty", 20),
            Map.entry("thirty", 30), Map.entry("forty", 40), Map.entry("fifty", 50), Map.entry("sixty", 60),
            Map.entry("seventy", 70), Map.entry("eighty", 80), Map.entry("ninety", 90),
            // ordinals
            Map.entry("first", 1), Map.entry("second", 2), Map.entry("third", 3), Map.entry("fourth", 4),
            Map.entry("fifth", 5), Map.entry("sixth", 6), Map.entry("seventh", 7), Map.entry("eighth", 8),
            Map.entry("ninth", 9), Map.entry("tenth", 10), Map.entry("eleventh", 11), Map.entry("twelfth", 12),
            Map.entry("thirteenth", 13), Map.entry("fourteenth", 14), Map.entry("fifteenth", 15),
            Map.entry("sixteenth", 16), Map.entry("seventeenth", 17), Map.entry("eighteenth", 18),
            Map.entry("nineteenth", 19), Map.entry("twentieth", 20), Map.entry("thirtieth", 30));

    private static final Map<String, Integer> SCALES = Map.of("hundred", 100, "thousand", 1000, "million", 1_000_000);

    /**
     * Replaces runs of number words in a token list with their digit form. "a"/"and" inside a number run are
     * absorbed ("a hundred and five" → 105). Tokens that are not number words are returned unchanged.
     */
    static List<String> toDigits(List<String> tokens) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            int j = i;
            long total = 0;
            long current = 0;
            boolean any = false;
            while (j < tokens.size()) {
                String t = tokens.get(j);
                if (UNITS.containsKey(t)) {
                    current += UNITS.get(t);
                    any = true;
                } else if (SCALES.containsKey(t) && any) {
                    int scale = SCALES.get(t);
                    if (scale == 100) {
                        current *= 100;
                    } else {
                        total += current * scale;
                        current = 0;
                    }
                } else if (t.equals("and") && any && j + 1 < tokens.size() && UNITS.containsKey(tokens.get(j + 1))) {
                    // "one hundred and five"
                } else if (t.equals("a") && !any && j + 1 < tokens.size() && SCALES.containsKey(tokens.get(j + 1))) {
                    current = 1;
                    any = true;
                } else {
                    break;
                }
                j++;
            }
            if (any) {
                out.add(Long.toString(total + current));
                i = j;
            } else {
                out.add(tokens.get(i));
                i++;
            }
        }
        return out;
    }
}
