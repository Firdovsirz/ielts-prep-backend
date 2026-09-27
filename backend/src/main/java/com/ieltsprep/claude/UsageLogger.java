package com.ieltsprep.claude;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes api_usage rows in their own transaction so failed calls are still recorded. */
@Component
public class UsageLogger {

    private static final Logger log = LoggerFactory.getLogger(UsageLogger.class);

    private final ApiUsageRepository repo;
    private final PricingService pricing;
    private final Clock clock;

    public UsageLogger(ApiUsageRepository repo, PricingService pricing, Clock clock) {
        this.repo = repo;
        this.pricing = pricing;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal record(String model, String purpose, String ref, long input, long output, long cacheRead,
            long cacheWrite, boolean batch, boolean success, String error, Long durationMs) {
        BigDecimal cost = pricing.cost(model, input, output, cacheRead, cacheWrite, batch);
        ApiUsage u = new ApiUsage();
        u.setModel(model);
        u.setPurpose(truncate(purpose, 32));
        u.setRef(truncate(ref, 255));
        u.setInputTokens((int) input);
        u.setOutputTokens((int) output);
        u.setCacheReadTokens((int) cacheRead);
        u.setCacheWriteTokens((int) cacheWrite);
        u.setCostUsd(cost);
        u.setBatch(batch);
        u.setSuccess(success);
        u.setError(truncate(error, 2000));
        u.setDurationMs(durationMs == null ? null : durationMs.intValue());
        u.setCreatedAt(Instant.now(clock));
        repo.save(u);
        log.info("claude {} {} in={} out={} cacheR={} cacheW={} cost=${}{}", purpose, model, input, output, cacheRead,
                cacheWrite, cost, success ? "" : " FAILED: " + error);
        return cost;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
