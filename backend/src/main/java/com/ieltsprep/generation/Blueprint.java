package com.ieltsprep.generation;

import com.ieltsprep.content.TaskType;
import java.util.List;
import java.util.Map;

/**
 * How to generate and verify one task type. Implementations choose variety (question-type plans, topics, chart
 * types), build the verification input (never including the answer key) and judge the verifier's report.
 */
public interface Blueprint {

    TaskType type();

    /** Buckets the background buffer keeps stocked; a single {@code null} means the whole task type. */
    List<String> bufferVariants();

    GenerationPlan plan(GenerationRequest request);

    String verifyPrompt();

    Map<String, Object> verifyVars(Object content);

    Verdict judge(Object content, VerificationReport report);
}
