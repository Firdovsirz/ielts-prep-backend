package com.ieltsprep.band;

import com.ieltsprep.content.ExamType;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Pure band arithmetic: raw-score conversion for Listening and Reading (Academic and General Training differ),
 * criterion averaging for Writing/Speaking, and the overall-band rounding rule.
 *
 * <p>The conversion tables are the widely published indicative tables (IELTS adjusts slightly per test version);
 * the same values are documented in {@code data/descriptors/score-conversion.json}.
 */
@Component
public class BandCalculator {

    /** Minimum raw score (out of 40) → band. */
    static final NavigableMap<Integer, Double> LISTENING = table(
            39, 9.0, 37, 8.5, 35, 8.0, 32, 7.5, 30, 7.0, 26, 6.5, 23, 6.0, 18, 5.5, 16, 5.0, 13, 4.5, 10, 4.0,
            8, 3.5, 6, 3.0, 4, 2.5, 2, 2.0, 1, 1.0, 0, 0.0);

    static final NavigableMap<Integer, Double> READING_ACADEMIC = table(
            39, 9.0, 37, 8.5, 35, 8.0, 33, 7.5, 30, 7.0, 27, 6.5, 23, 6.0, 19, 5.5, 15, 5.0, 13, 4.5, 10, 4.0,
            8, 3.5, 6, 3.0, 4, 2.5, 2, 2.0, 1, 1.0, 0, 0.0);

    static final NavigableMap<Integer, Double> READING_GENERAL = table(
            40, 9.0, 39, 8.5, 37, 8.0, 36, 7.5, 34, 7.0, 32, 6.5, 30, 6.0, 27, 5.5, 23, 5.0, 19, 4.5, 15, 4.0,
            12, 3.5, 9, 3.0, 6, 2.5, 3, 2.0, 1, 1.0, 0, 0.0);

    public static final int FULL_TEST_QUESTIONS = 40;

    public double listeningBand(int raw) {
        return lookup(LISTENING, raw);
    }

    public double readingBand(int raw, ExamType examType) {
        return lookup(examType == ExamType.GENERAL ? READING_GENERAL : READING_ACADEMIC, raw);
    }

    /**
     * Band estimate for a partial test (one passage or section): the raw score is scaled to 40 questions first.
     * Less reliable than a full test and labelled as an estimate in the UI.
     */
    public double scaledListeningBand(int raw, int max) {
        return listeningBand(scaleTo40(raw, max));
    }

    public double scaledReadingBand(int raw, int max, ExamType examType) {
        return readingBand(scaleTo40(raw, max), examType);
    }

    static int scaleTo40(int raw, int max) {
        if (max <= 0) {
            return 0;
        }
        if (max == FULL_TEST_QUESTIONS) {
            return clamp(raw);
        }
        return clamp((int) Math.round(raw * (double) FULL_TEST_QUESTIONS / max));
    }

    /**
     * Overall band: mean of the four module bands rounded to the nearest half band; a mean ending in .25 rounds up to
     * .5 and one ending in .75 rounds up to the next whole band. Computed in exact half-band units.
     */
    public double overallBand(double listening, double reading, double writing, double speaking) {
        int halfUnits = toHalfUnits(listening) + toHalfUnits(reading) + toHalfUnits(writing) + toHalfUnits(speaking);
        return Math.floorDiv(halfUnits + 2, 4) / 2.0;
    }

    /** Nearest half band, ties rounding up (used for weighted combinations such as Task 1 + 2×Task 2). */
    public double roundToHalf(double value) {
        return Math.floor(value * 2 + 0.5 + 1e-9) / 2.0;
    }

    /**
     * Writing/Speaking task band from its criterion bands: the mean rounded DOWN to the nearest half band. IELTS does
     * not publish this step; rounding down is the conservative examiner-training convention.
     */
    public double criteriaBand(Collection<Double> criterionBands) {
        if (criterionBands.isEmpty()) {
            throw new IllegalArgumentException("No criterion bands");
        }
        double mean = criterionBands.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return Math.floor(mean * 2 + 1e-9) / 2.0;
    }

    /** Writing module band: Task 2 counts twice as much as Task 1. */
    public double writingBand(double task1, double task2) {
        return roundToHalf((task1 + 2 * task2) / 3.0);
    }

    private static int toHalfUnits(double band) {
        if (band < 0 || band > 9) {
            throw new IllegalArgumentException("Band out of range: " + band);
        }
        long units = Math.round(band * 2);
        if (Math.abs(units - band * 2) > 1e-9) {
            throw new IllegalArgumentException("Module bands are whole or half bands: " + band);
        }
        return (int) units;
    }

    private static double lookup(NavigableMap<Integer, Double> table, int raw) {
        return table.floorEntry(clamp(raw)).getValue();
    }

    private static int clamp(int raw) {
        return Math.max(0, Math.min(FULL_TEST_QUESTIONS, raw));
    }

    private static NavigableMap<Integer, Double> table(Object... pairs) {
        NavigableMap<Integer, Double> map = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Integer) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }

    /** Conversion tables for display (min raw → band). */
    public Map<String, NavigableMap<Integer, Double>> tables() {
        return Map.of("listening", LISTENING, "reading_academic", READING_ACADEMIC, "reading_general", READING_GENERAL);
    }
}
