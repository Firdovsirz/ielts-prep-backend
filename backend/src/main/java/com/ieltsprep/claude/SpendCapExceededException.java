package com.ieltsprep.claude;

import java.math.BigDecimal;

public class SpendCapExceededException extends RuntimeException {

    public SpendCapExceededException(BigDecimal spent, BigDecimal limit, boolean background) {
        super(background
                ? "Background generation paused: today's API spend $%s has reached the background share ($%s) of the daily cap."
                        .formatted(spent.setScale(2, java.math.RoundingMode.HALF_UP), limit.setScale(2, java.math.RoundingMode.HALF_UP))
                : "Daily API spend cap reached ($%s of $%s). Generation and grading resume tomorrow, or raise CLAUDE_DAILY_SPEND_CAP_USD."
                        .formatted(spent.setScale(2, java.math.RoundingMode.HALF_UP), limit.setScale(2, java.math.RoundingMode.HALF_UP)));
    }
}
