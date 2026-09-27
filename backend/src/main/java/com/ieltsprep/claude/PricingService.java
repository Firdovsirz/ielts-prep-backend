package com.ieltsprep.claude;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Converts token usage to USD using {@code claude.pricing}. */
@Component
public class PricingService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final ClaudeProperties.Price FALLBACK =
            new ClaudeProperties.Price(BigDecimal.valueOf(5), BigDecimal.valueOf(25), BigDecimal.valueOf(0.5));

    private final ClaudeProperties props;

    public PricingService(ClaudeProperties props) {
        this.props = props;
    }

    public BigDecimal cost(String model, long input, long output, long cacheRead, long cacheWrite, boolean batch) {
        ClaudeProperties.Price p = props.pricing() == null ? FALLBACK : props.pricing().getOrDefault(model, FALLBACK);
        BigDecimal writeMultiplier = props.longCacheTtl() ? BigDecimal.valueOf(2) : BigDecimal.valueOf(1.25);
        BigDecimal cacheReadPrice = p.cacheRead() != null ? p.cacheRead() : p.input().multiply(BigDecimal.valueOf(0.1));
        BigDecimal total = p.input().multiply(BigDecimal.valueOf(input))
                .add(p.output().multiply(BigDecimal.valueOf(output)))
                .add(cacheReadPrice.multiply(BigDecimal.valueOf(cacheRead)))
                .add(p.input().multiply(writeMultiplier).multiply(BigDecimal.valueOf(cacheWrite)))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        if (batch) {
            total = total.multiply(BigDecimal.valueOf(0.5));
        }
        return total.setScale(6, RoundingMode.HALF_UP);
    }
}
