package com.ieltsprep.generation;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Output of every verification prompt (prompts/schemas/verification-report.schema.json). Question items carry the
 * verifier's blind answers, which are compared with the generator's key in code; review-only items leave them empty.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VerificationReport(
        List<BlindAnswer> blindAnswers,
        List<Issue> issues,
        String verdict,
        String summary) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BlindAnswer(String id, String answer, String confidence, String evidence) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Issue(String id, String severity, String kind, String detail) {}

    public boolean passed() {
        return "PASS".equalsIgnoreCase(verdict);
    }

    public List<Issue> blockers() {
        return issues == null ? List.of() : issues.stream().filter(i -> "blocker".equalsIgnoreCase(i.severity())).toList();
    }
}
