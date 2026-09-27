package com.ieltsprep.vocab;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Mirrors prompts/schemas/vocab-word-bank.schema.json. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WordBank(String topic, List<Entry> words) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Entry(
            String word,
            String partOfSpeech,
            String definition,
            String example,
            List<String> collocations,
            String cefr) {}
}
