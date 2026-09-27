package com.ieltsprep.speaking;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The examiner's next move. stage: PART1 | PART2_LONG_TURN (show the cue card: prep then talk) | PART2_ROUNDING |
 * PART3 | END. part/questionIndex identify the answer that should follow.
 */
@Schema(name = "ExaminerTurn")
public record ExaminerTurn(String stage, int part, int questionIndex, String utterance, boolean conversational,
        @Schema(nullable = true) Integer prepSeconds, @Schema(nullable = true) Integer talkSeconds,
        @Schema(nullable = true) Integer answerSeconds) {}
