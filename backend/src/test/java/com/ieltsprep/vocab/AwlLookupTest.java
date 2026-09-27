package com.ieltsprep.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AwlLookupTest {

    final AwlLookup awl = new AwlLookup(Path.of("../data/descriptors/awl.json"));

    @Test
    void loadsAllFiveHundredSeventyHeadwords() {
        assertThat(awl.size()).isEqualTo(570);
    }

    @Test
    void matchesHeadwordsPluralsAndWordFamilyMembers() {
        assertThat(awl.sublist("analyse")).contains(1);
        assertThat(awl.sublist("Hypothesis")).contains(4);
        assertThat(awl.sublist("theories")).contains(1);
        assertThat(awl.sublist("policies")).contains(1);
        assertThat(awl.sublist("processes")).contains(1);
        assertThat(awl.sublist("analysis")).contains(1);
        assertThat(awl.sublist("economic")).contains(1);
        assertThat(awl.sublist("environmental")).contains(1);
        assertThat(awl.sublist("sustainable")).contains(5);
    }

    @Test
    void ignoresEverydayWordsAndPhrases() {
        assertThat(awl.sublist("cat")).isEmpty();
        assertThat(awl.sublist("house")).isEmpty();
        assertThat(awl.sublist("carbon footprint")).isEmpty();
        assertThat(awl.sublist(null)).isEmpty();
    }
}
