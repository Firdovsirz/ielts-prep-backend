package com.ieltsprep.claude;

import java.util.Locale;

/**
 * Which configured model a call uses ({@code claude.models.<key>}). Strong model for generation, verification,
 * grading, coaching and the examiner; the fast model for classification, tagging and enrichment.
 */
public enum ModelRoute {
    GENERATION,
    VERIFICATION,
    GRADING,
    COACHING,
    EXAMINER,
    FAST;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
