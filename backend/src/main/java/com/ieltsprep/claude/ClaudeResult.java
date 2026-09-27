package com.ieltsprep.claude;

import java.math.BigDecimal;

public record ClaudeResult<T>(T value, String rawJson, String model, long inputTokens, long outputTokens, BigDecimal costUsd) {}
