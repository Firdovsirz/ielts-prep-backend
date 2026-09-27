package com.ieltsprep.marking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Marks one Listening/Reading answer the way an IELTS marker would:
 * <ul>
 *   <li>case, surrounding punctuation and extra spaces are ignored;</li>
 *   <li>British and American spellings are both accepted; misspellings are wrong;</li>
 *   <li>numbers may be written as digits or words ("15" = "fifteen", "3rd" = "third"), thousands separators
 *       and currency symbols are ignored, 7:30 = 7.30, "per cent" = "percent" = "%";</li>
 *   <li>optional words in the key are marked with parentheses: "(the) harbour"; a leading article the key omits
 *       is tolerated;</li>
 *   <li>dates may be given in either order ("15 March" = "March 15");</li>
 *   <li>answers longer than the word limit are wrong even if they contain the right words.</li>
 * </ul>
 */
public final class AnswerMarker {

    public record Result(boolean correct, boolean blank, String reason) {
        static Result blankAnswer() {
            return new Result(false, true, "No answer");
        }
    }

    private static final Set<String> ARTICLES = Set.of("a", "an", "the");
    private static final Set<String> MONTHS = Set.of("january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december", "jan", "feb", "mar", "apr", "jun", "jul", "aug",
            "sep", "sept", "oct", "nov", "dec");
    private static final Pattern ORDINAL = Pattern.compile("^(\\d+)(st|nd|rd|th)$");
    private static final Pattern OPTIONAL = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2})[:.](\\d{2})");

    private AnswerMarker() {}

    /** Text answers (completion, short answer). {@code wordLimit} 0 means no limit. */
    public static Result markText(String given, List<String> accepted, int wordLimit) {
        if (given == null || given.isBlank()) {
            return Result.blankAnswer();
        }
        if (wordLimit > 0 && countWords(given) > wordLimit) {
            return new Result(false, false, "More than " + wordLimit + " word" + (wordLimit == 1 ? "" : "s"));
        }
        List<String> givenTokens = normalise(given);
        for (String key : accepted) {
            for (String variant : expandOptional(key)) {
                if (equivalent(givenTokens, normalise(variant))) {
                    return new Result(true, false, null);
                }
            }
        }
        return new Result(false, false, null);
    }

    /** Choice answers (letters, roman numerals, TRUE/FALSE/NOT GIVEN, YES/NO/NOT GIVEN). */
    public static Result markChoice(String given, List<String> accepted) {
        if (given == null || given.isBlank()) {
            return Result.blankAnswer();
        }
        String g = normaliseChoice(given);
        boolean ok = accepted.stream().map(AnswerMarker::normaliseChoice).anyMatch(g::equals);
        return new Result(ok, false, null);
    }

    /**
     * "Choose TWO letters" groups: each question number carries one mark, and the letters may be given in any order.
     * Returns one result per given answer (same order as {@code givenPerQuestion}).
     */
    public static List<Result> markUnordered(List<String> givenPerQuestion, List<String> keyLetters) {
        Set<String> remaining = keyLetters.stream().map(AnswerMarker::normaliseChoice).collect(Collectors.toCollection(HashSet::new));
        List<Result> results = new ArrayList<>();
        for (String given : givenPerQuestion) {
            if (given == null || given.isBlank()) {
                results.add(Result.blankAnswer());
                continue;
            }
            String g = normaliseChoice(given);
            boolean ok = remaining.remove(g);
            results.add(new Result(ok, false, ok ? null : "Letter not in the key or already counted"));
        }
        return results;
    }

    static String normaliseChoice(String s) {
        String t = s.trim().replaceAll("[\\s.]+$", "").replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return switch (t) {
            case "T" -> "TRUE";
            case "F" -> "FALSE";
            case "Y" -> "YES";
            case "N" -> "NO";
            case "NG", "NOTGIVEN", "NOT-GIVEN" -> "NOT GIVEN";
            default -> t;
        };
    }

    /** IELTS word count: hyphenated words count as one word, and a number (digits or words) counts as one. */
    public static int countWords(String s) {
        String cleaned = s.trim().toLowerCase(Locale.ROOT).replaceAll("[\\u2013\\u2014]", "-");
        if (cleaned.isEmpty()) {
            return 0;
        }
        List<String> tokens = Arrays.stream(cleaned.split("\\s+"))
                .map(w -> w.replaceAll("^[\\p{Punct}&&[^$%]]+|[\\p{Punct}&&[^%]]+$", ""))
                .filter(w -> !w.isEmpty())
                .toList();
        return NumberWords.toDigits(tokens).size();
    }

    static List<String> expandOptional(String key) {
        Matcher m = OPTIONAL.matcher(key);
        if (!m.find()) {
            return List.of(key);
        }
        String with = squash(key.substring(0, m.start()) + m.group(1) + key.substring(m.end()));
        String without = squash(key.substring(0, m.start()) + key.substring(m.end()));
        Set<String> out = new LinkedHashSet<>();
        out.addAll(expandOptional(with));
        out.addAll(expandOptional(without));
        return new ArrayList<>(out);
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    static List<String> normalise(String raw) {
        String s = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[\\u2018\\u2019`]", "'")
                .replaceAll("[\\u201C\\u201D]", "\"")
                .replaceAll("[\\u2013\\u2014]", "-")
                .replace("p.m.", "pm").replace("a.m.", "am");
        s = TIME.matcher(s).replaceAll("$1.$2");
        s = s.replaceAll("(?<=\\d),(?=\\d{3})", "") // 1,500 → 1500, 2,000,000 → 2000000
                .replaceAll("(\\d)(am|pm)\\b", "$1 $2")
                .replaceAll("[£$€]", "")
                .replace("per cent", "percent")
                .replace("%", " percent")
                .replace("-", " ")
                .replace("'s", "s")
                .replaceAll("[\"',;:!?()\\[\\]]", " ")
                .replaceAll("\\.(?!\\d)", " ");
        List<String> tokens = new ArrayList<>(Arrays.asList(s.trim().split("\\s+")));
        tokens.removeIf(String::isEmpty);
        tokens = NumberWords.toDigits(tokens);
        List<String> out = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            Matcher ord = ORDINAL.matcher(t);
            out.add(ord.matches() ? ord.group(1) : SpellingVariants.canonical(t));
        }
        return out;
    }

    private static boolean equivalent(List<String> given, List<String> key) {
        if (given.equals(key)) {
            return true;
        }
        List<String> g = stripLeadingArticle(given);
        List<String> k = stripLeadingArticle(key);
        if (g.equals(k)) {
            return true;
        }
        // dates in either order: "15 march" = "march 15" (also "the 15th of march")
        if (containsMonth(g) && containsMonth(k)) {
            List<String> gd = new ArrayList<>(g);
            List<String> kd = new ArrayList<>(k);
            gd.removeIf(t -> t.equals("of") || t.equals("the"));
            kd.removeIf(t -> t.equals("of") || t.equals("the"));
            return gd.size() == kd.size() && new HashSet<>(gd).equals(new HashSet<>(kd));
        }
        return false;
    }

    private static List<String> stripLeadingArticle(List<String> tokens) {
        return !tokens.isEmpty() && ARTICLES.contains(tokens.getFirst()) ? tokens.subList(1, tokens.size()) : tokens;
    }

    private static boolean containsMonth(List<String> tokens) {
        return tokens.stream().anyMatch(MONTHS::contains);
    }
}
