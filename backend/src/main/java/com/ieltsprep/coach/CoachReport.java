package com.ieltsprep.coach;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "coach_reports")
@Getter
@Setter
public class CoachReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate weekStart;
    private LocalDate weekEnd;
    /** JSON of {@link CoachReportContent}. */
    private String report;
    /** JSON of {@link WeeklyStats}. */
    private String stats;
    /** SCHEDULED or MANUAL. */
    private String triggerType;
    /** AI (Claude) or RULES (offline fallback). */
    private String generatedBy;
    private Instant createdAt;
}
