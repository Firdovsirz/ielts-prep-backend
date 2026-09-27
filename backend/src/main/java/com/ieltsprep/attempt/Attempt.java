package com.ieltsprep.attempt;

import com.ieltsprep.content.Skill;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A submission against one item: answers, score, band estimate, per-question results and Claude feedback. */
@Entity
@Table(name = "attempts")
@Getter
@Setter
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Long itemId;

    @Enumerated(EnumType.STRING)
    private Skill module;

    private String taskType;
    private String myAnswers;
    private Integer rawScore;
    private Integer maxScore;
    private BigDecimal bandEstimate;
    private String perCriterionBands;
    private String perQuestion;
    private String feedback;
    private Integer durationSeconds;
    private Integer wordCount;

    @Enumerated(EnumType.STRING)
    private AttemptStatus status;

    private Instant submittedAt;
    private Instant gradedAt;
}
