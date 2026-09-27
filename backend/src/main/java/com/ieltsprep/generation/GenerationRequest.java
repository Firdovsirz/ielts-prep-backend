package com.ieltsprep.generation;

import com.ieltsprep.content.TaskType;
import java.util.Map;

/**
 * @param variant  bucket to generate for (e.g. "P2", "S4", "LINE", "OPINION", a grammar area); null = blueprint's choice
 * @param extra    blueprint-specific inputs (e.g. the error rows behind an error-driven drill)
 * @param feedback reasons the previous attempt failed verification, fed back into the regeneration prompt
 */
public record GenerationRequest(TaskType type, String variant, boolean background, Map<String, Object> extra, String feedback) {

    public static GenerationRequest of(TaskType type, String variant, boolean background) {
        return new GenerationRequest(type, variant, background, Map.of(), null);
    }

    public GenerationRequest withFeedback(String feedback) {
        return new GenerationRequest(type, variant, background, extra, feedback);
    }
}
