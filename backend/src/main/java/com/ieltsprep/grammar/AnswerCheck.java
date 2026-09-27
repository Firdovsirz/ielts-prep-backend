package com.ieltsprep.grammar;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Mirrors prompts/schemas/grammar-answer-check.schema.json. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnswerCheck(List<Result> results) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(String id, boolean correct, String feedback, String improvedVersion) {}
}
