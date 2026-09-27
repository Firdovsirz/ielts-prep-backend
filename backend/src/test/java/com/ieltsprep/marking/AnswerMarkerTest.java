package com.ieltsprep.marking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnswerMarkerTest {

    private static boolean text(String given, String key, int limit) {
        return AnswerMarker.markText(given, List.of(key), limit).correct();
    }

    @ParameterizedTest(name = "\"{0}\" matches key \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "colour|color", "color|colour", "centre|center", "organisation|organization", "organization|organisation",
            "analysed|analyzed", "travelled|traveled", "traveled|travelled", "cancelling|canceling", "theatre|theater",
            "programme|program", "neighbours|neighbors", "grey|gray", "catalogue|catalog", "defence|defense",
            "jewellery|jewelry", "aluminium|aluminum", "archaeology|archeology", "labelled|labeled", "modelling|modeling"})
    void britishAndAmericanSpellingsAreBothAccepted(String given, String key) {
        assertThat(text(given, key, 2)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" rejected for \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "colur|colour", "recieve|receive", "accomodation|accommodation", "libary|library", "harbours|harbour",
            "compeled|compelled", "fourty|forty", "goverment|government"})
    void misspellingsAndPluralsAreWrong(String given, String key) {
        assertThat(text(given, key, 2)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" = \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "15|fifteen", "fifteen|15", "twenty-five|25", "25|twenty five", "one hundred and fifty|150",
            "1,500|1500", "3rd|third", "third|3", "£45|45", "7:30|7.30", "7.30pm|7.30 pm", "40%|40 per cent",
            "40 percent|40%", "15 March|March 15", "the 15th of March|15 March", "2,000,000|2000000"})
    void numbersDatesAndSymbolsAreNormalised(String given, String key) {
        assertThat(text(given, key, 0)).isTrue();
    }

    @Test
    void numberWordsCountAsOneWord() {
        assertThat(AnswerMarker.countWords("one hundred and fifty")).isEqualTo(1);
        assertThat(text("one hundred and fifty", "150", 1)).isTrue();
    }

    @Test
    void wordLimitIsEnforced() {
        assertThat(AnswerMarker.markText("the old stone harbour", List.of("stone harbour"), 2).correct()).isFalse();
        assertThat(AnswerMarker.markText("the old stone harbour", List.of("stone harbour"), 2).reason()).contains("More than 2");
        assertThat(text("stone harbour", "stone harbour", 2)).isTrue();
        // hyphenated words and numbers count as one word
        assertThat(AnswerMarker.countWords("well-known 1,500 people")).isEqualTo(3);
    }

    @Test
    void optionalWordsInTheKey() {
        assertThat(text("the harbour", "(the) harbour", 2)).isTrue();
        assertThat(text("harbour", "(the) harbour", 2)).isTrue();
        assertThat(text("small opening", "(small) opening", 2)).isTrue();
        assertThat(AnswerMarker.expandOptional("(the) (old) mill")).containsExactlyInAnyOrder("the old mill", "the mill", "old mill", "mill");
    }

    @Test
    void leadingArticleTolerated() {
        assertThat(text("the library", "library", 2)).isTrue();
        assertThat(text("a library", "library", 2)).isTrue();
    }

    @Test
    void caseAndPunctuationIgnored() {
        assertThat(text("  Harbour. ", "harbour", 1)).isTrue();
        assertThat(text("HARDWICK", "Hardwick", 1)).isTrue();
    }

    @Test
    void alternativesInKey() {
        assertThat(AnswerMarker.markText("photo", List.of("photograph", "photo", "picture"), 1).correct()).isTrue();
    }

    @Test
    void blankIsFlagged() {
        AnswerMarker.Result r = AnswerMarker.markText("  ", List.of("x"), 1);
        assertThat(r.correct()).isFalse();
        assertThat(r.blank()).isTrue();
    }

    @Test
    void choiceAnswers() {
        assertThat(AnswerMarker.markChoice("b", List.of("B")).correct()).isTrue();
        assertThat(AnswerMarker.markChoice("NG", List.of("NOT GIVEN")).correct()).isTrue();
        assertThat(AnswerMarker.markChoice("not given", List.of("NOT GIVEN")).correct()).isTrue();
        assertThat(AnswerMarker.markChoice("TRUE", List.of("FALSE")).correct()).isFalse();
        assertThat(AnswerMarker.markChoice("iv", List.of("iv")).correct()).isTrue();
    }

    @Test
    void chooseTwoIsOrderIndependentAndCountsEachLetterOnce() {
        List<AnswerMarker.Result> swapped = AnswerMarker.markUnordered(List.of("D", "B"), List.of("B", "D"));
        assertThat(swapped).allMatch(AnswerMarker.Result::correct);
        List<AnswerMarker.Result> duplicate = AnswerMarker.markUnordered(List.of("B", "B"), List.of("B", "D"));
        assertThat(duplicate.get(0).correct()).isTrue();
        assertThat(duplicate.get(1).correct()).isFalse();
        List<AnswerMarker.Result> oneWrong = AnswerMarker.markUnordered(List.of("A", "D"), List.of("B", "D"));
        assertThat(oneWrong.stream().filter(AnswerMarker.Result::correct).count()).isEqualTo(1);
    }
}
