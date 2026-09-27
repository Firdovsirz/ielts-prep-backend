package com.ieltsprep.claude;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One row per Claude API call (interactive or batch result), with tokens and cost. */
@Entity
@Table(name = "api_usage")
@Getter
@Setter
public class ApiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String model;
    private String purpose;
    private int inputTokens;
    private int outputTokens;
    private int cacheReadTokens;
    private int cacheWriteTokens;
    private BigDecimal costUsd = BigDecimal.ZERO;
    private boolean batch;
    private boolean success;
    private String error;
    private Integer durationMs;
    private String ref;
    private Instant createdAt;
}
