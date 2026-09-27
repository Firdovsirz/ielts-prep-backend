package com.ieltsprep.claude;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("claude")
public record ClaudeProperties(
        String apiKey,
        String baseUrl,
        int timeoutSeconds,
        int maxAttempts,
        long initialBackoffMs,
        BigDecimal dailySpendCapUsd,
        double backgroundShare,
        String cacheTtl,
        Map<String, String> models,
        Map<String, String> effort,
        Map<String, Long> maxTokens,
        Map<String, Price> pricing) {

    /** USD per million tokens. */
    public record Price(BigDecimal input, BigDecimal output, BigDecimal cacheRead) {}

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model(ModelRoute route) {
        String model = models.get(route.key());
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("No model configured for claude.models." + route.key());
        }
        return model.trim();
    }

    public String effort(ModelRoute route) {
        String value = effort == null ? null : effort.get(route.key());
        return value == null || value.isBlank() ? null : value.trim();
    }

    public long maxTokens(ModelRoute route) {
        Long value = maxTokens == null ? null : maxTokens.get(route.key());
        return value == null ? 16_000L : value;
    }

    public boolean longCacheTtl() {
        return "1h".equalsIgnoreCase(cacheTtl);
    }
}
