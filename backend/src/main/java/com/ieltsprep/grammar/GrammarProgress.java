package com.ieltsprep.grammar;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Per-area grammar proficiency (0–100), seeded by the diagnostic and updated by every exercise and drill. */
@Entity
@Table(name = "grammar_progress")
@Getter
@Setter
public class GrammarProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String area;
    private double proficiency;
    private Double diagnosticScore;
    private Instant lastTested;
    private int drillsDone;
    private int questionsAnswered;
    private int correctAnswers;
    private double accuracy;
    private Instant lessonViewedAt;
}
