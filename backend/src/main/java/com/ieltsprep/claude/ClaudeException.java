package com.ieltsprep.claude;

/** A Claude call that failed after retries (or could not be attempted). */
public class ClaudeException extends RuntimeException {

    private final String code;

    public ClaudeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ClaudeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ClaudeException notConfigured() {
        return new ClaudeException("CLAUDE_NOT_CONFIGURED",
                "ANTHROPIC_API_KEY is not set. Add it to .env and restart the backend to enable generation and grading.");
    }

    /** Transient failure (429, 5xx, overloaded, network) — safe to retry with backoff. */
    public static class Retryable extends ClaudeException {
        public Retryable(String message, Throwable cause) {
            super("CLAUDE_UNAVAILABLE", message, cause);
        }
    }
}
