package com.ieltsprep.speaking;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Mirrors the speaking-part1/2/3 schemas. */
public final class SpeakingPrompts {

    private SpeakingPrompts() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Part1Topic(String topic, List<String> questions) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Part1(List<Part1Topic> topics) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CueCard(String prompt, List<String> bullets, String explain) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Part2(String topic, String theme, CueCard cueCard, List<String> roundingOffQuestions) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Part3Question(String subTopic, String question) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Part3(String theme, String linkedPart2Topic, List<Part3Question> questions) {}
}
