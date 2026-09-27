package com.ieltsprep.band;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.ExamType;
import java.nio.file.Path;
import java.util.List;
import java.util.NavigableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BandCalculatorTest {

    private final BandCalculator calc = new BandCalculator();

    @ParameterizedTest(name = "listening raw {0} → {1}")
    @CsvSource({"40,9.0", "39,9.0", "38,8.5", "37,8.5", "36,8.0", "35,8.0", "34,7.5", "32,7.5", "31,7.0", "30,7.0",
            "29,6.5", "26,6.5", "25,6.0", "23,6.0", "22,5.5", "18,5.5", "17,5.0", "16,5.0", "15,4.5", "13,4.5",
            "12,4.0", "10,4.0", "0,0.0"})
    void listeningConversion(int raw, double band) {
        assertThat(calc.listeningBand(raw)).isEqualTo(band);
    }

    @ParameterizedTest(name = "academic reading raw {0} → {1}")
    @CsvSource({"40,9.0", "39,9.0", "38,8.5", "37,8.5", "36,8.0", "35,8.0", "34,7.5", "33,7.5", "32,7.0", "30,7.0",
            "29,6.5", "27,6.5", "26,6.0", "23,6.0", "22,5.5", "19,5.5", "18,5.0", "15,5.0", "14,4.5", "13,4.5", "10,4.0"})
    void academicReadingConversion(int raw, double band) {
        assertThat(calc.readingBand(raw, ExamType.ACADEMIC)).isEqualTo(band);
    }

    @ParameterizedTest(name = "general reading raw {0} → {1}")
    @CsvSource({"40,9.0", "39,8.5", "38,8.0", "37,8.0", "36,7.5", "35,7.0", "34,7.0", "33,6.5", "32,6.5", "31,6.0",
            "30,6.0", "29,5.5", "27,5.5", "26,5.0", "23,5.0", "22,4.5", "19,4.5", "18,4.0", "15,4.0"})
    void generalReadingConversion(int raw, double band) {
        assertThat(calc.readingBand(raw, ExamType.GENERAL)).isEqualTo(band);
    }

    @Test
    void academicAndGeneralReadingDiffer() {
        assertThat(calc.readingBand(30, ExamType.ACADEMIC)).isEqualTo(7.0);
        assertThat(calc.readingBand(30, ExamType.GENERAL)).isEqualTo(6.0);
    }

    @Test
    void rawScoresOutsideRangeAreClamped() {
        assertThat(calc.listeningBand(45)).isEqualTo(9.0);
        assertThat(calc.listeningBand(-3)).isEqualTo(0.0);
    }

    @ParameterizedTest(name = "overall {0},{1},{2},{3} → {4}")
    @CsvSource({
            "6.5,6.5,5.0,7.0,6.5",   // mean 6.25 → rounds up to 6.5
            "6.5,7.0,7.0,6.5,7.0",   // mean 6.75 → rounds up to 7.0
            "6.0,6.0,6.0,6.5,6.0",   // mean 6.125 → 6.0
            "6.0,6.5,6.5,6.5,6.5",   // mean 6.375 → 6.5
            "6.5,6.5,6.5,7.0,6.5",   // mean 6.625 → 6.5
            "6.5,7.0,7.0,7.0,7.0",   // mean 6.875 → 7.0
            "8.0,8.0,8.0,8.0,8.0",
            "9.0,9.0,8.5,8.5,9.0",   // mean 8.75 → 9.0
            "4.0,4.5,4.5,5.0,4.5",
            "7.5,7.0,6.5,7.0,7.0"    // mean 7.0
    })
    void overallBandRounding(double l, double r, double w, double s, double expected) {
        assertThat(calc.overallBand(l, r, w, s)).isEqualTo(expected);
    }

    @Test
    void overallRejectsNonHalfBands() {
        assertThatThrownBy(() -> calc.overallBand(6.3, 6, 6, 6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void criteriaBandRoundsDownToHalf() {
        assertThat(calc.criteriaBand(List.of(7.0, 7.0, 6.0, 6.0))).isEqualTo(6.5);
        assertThat(calc.criteriaBand(List.of(7.0, 7.0, 7.0, 6.0))).isEqualTo(6.5); // 6.75 → 6.5
        assertThat(calc.criteriaBand(List.of(8.0, 7.0, 7.0, 7.0))).isEqualTo(7.0); // 7.25 → 7.0
        assertThat(calc.criteriaBand(List.of(6.0, 6.0, 7.0))).isEqualTo(6.0);      // 6.33 → 6.0
    }

    @Test
    void writingBandWeightsTask2Double() {
        assertThat(calc.writingBand(6.0, 7.0)).isEqualTo(6.5); // 6.67 → 6.5
        assertThat(calc.writingBand(6.5, 7.0)).isEqualTo(7.0); // 6.83 → 7.0
        assertThat(calc.writingBand(8.0, 6.0)).isEqualTo(6.5); // 6.67 → 6.5
        assertThat(calc.writingBand(7.0, 7.0)).isEqualTo(7.0);
    }

    @Test
    void partialTestsAreScaledTo40() {
        assertThat(BandCalculator.scaleTo40(13, 13)).isEqualTo(40);
        assertThat(BandCalculator.scaleTo40(10, 13)).isEqualTo(31);
        assertThat(calc.scaledReadingBand(10, 13, ExamType.ACADEMIC)).isEqualTo(7.0);
        assertThat(calc.scaledListeningBand(8, 10)).isEqualTo(7.5); // 32/40
    }

    @Test
    void tablesMatchPublishedJson() throws Exception {
        JsonNode doc = Json.MAPPER.readTree(Path.of("../data/descriptors/score-conversion.json").toFile());
        assertTable(doc.get("listening"), BandCalculator.LISTENING);
        assertTable(doc.get("reading_academic"), BandCalculator.READING_ACADEMIC);
        assertTable(doc.get("reading_general"), BandCalculator.READING_GENERAL);
    }

    private static void assertTable(JsonNode rows, NavigableMap<Integer, Double> table) {
        assertThat(rows.size()).isEqualTo(table.size());
        rows.forEach(r -> assertThat(table.get(r.get("min").asInt())).isEqualTo(r.get("band").asDouble()));
    }
}
