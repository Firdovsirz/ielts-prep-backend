package com.ieltsprep.claude;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Enforces the daily spend cap. Interactive calls may use the whole cap; background jobs (buffer top-up,
 * batches, coach report) stop at {@code claude.background-share} of it so grading still works later in the day.
 */
@Component
public class SpendGuard {

    @io.swagger.v3.oas.annotations.media.Schema(name = "SpendStatus")
    public record Status(
            BigDecimal spentToday,
            BigDecimal dailyCap,
            BigDecimal backgroundLimit,
            boolean capReached,
            boolean backgroundPaused,
            boolean apiKeyConfigured,
            BigDecimal spentLast7Days,
            long callsToday) {}

    private final ApiUsageRepository usage;
    private final ClaudeProperties props;
    private final Clock clock;
    private final ApiKeyStore keys;

    public SpendGuard(ApiUsageRepository usage, ClaudeProperties props, Clock clock, ApiKeyStore keys) {
        this.keys = keys;
        this.usage = usage;
        this.props = props;
        this.clock = clock;
    }

    public void ensureAllowed(boolean background) {
        BigDecimal spent = spentToday();
        BigDecimal cap = props.dailySpendCapUsd();
        if (spent.compareTo(cap) >= 0) {
            throw new SpendCapExceededException(spent, cap, false);
        }
        BigDecimal backgroundLimit = backgroundLimit();
        if (background && spent.compareTo(backgroundLimit) >= 0) {
            throw new SpendCapExceededException(spent, backgroundLimit, true);
        }
    }

    /** Remaining budget for a caller, used to size batches. */
    public BigDecimal remaining(boolean background) {
        BigDecimal limit = background ? backgroundLimit() : props.dailySpendCapUsd();
        return limit.subtract(spentToday()).max(BigDecimal.ZERO);
    }

    public BigDecimal spentToday() {
        return usage.sumCostSince(startOfToday());
    }

    public Status status() {
        BigDecimal spent = spentToday();
        BigDecimal cap = props.dailySpendCapUsd();
        BigDecimal bg = backgroundLimit();
        Instant weekAgo = startOfToday().minus(6, ChronoUnit.DAYS);
        return new Status(
                spent.setScale(4, RoundingMode.HALF_UP),
                cap,
                bg.setScale(2, RoundingMode.HALF_UP),
                spent.compareTo(cap) >= 0,
                spent.compareTo(bg) >= 0,
                keys.present(),
                usage.sumCostSince(weekAgo).setScale(4, RoundingMode.HALF_UP),
                usage.countSince(startOfToday()));
    }

    private BigDecimal backgroundLimit() {
        return props.dailySpendCapUsd().multiply(BigDecimal.valueOf(props.backgroundShare()));
    }

    private Instant startOfToday() {
        ZoneId zone = clock.getZone();
        return LocalDate.now(clock).atStartOfDay(zone).toInstant();
    }
}
