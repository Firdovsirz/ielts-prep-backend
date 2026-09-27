package com.ieltsprep.generation;

import java.util.Map;

/** What to ask Claude for: prompt file, template variables and provenance of the topic seed. */
public record GenerationPlan(
        String prompt,
        Map<String, Object> vars,
        String sourceUrl,
        String licence,
        String description) {}
