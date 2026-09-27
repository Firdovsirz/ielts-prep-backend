package com.ieltsprep.settings;

import com.ieltsprep.content.ExamType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Single-row table (id = 1) with the candidate's profile and preferences. */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private ExamType examType;

    private BigDecimal currentBand;
    private BigDecimal targetBand;
    private LocalDate testDate;

    @Enumerated(EnumType.STRING)
    private AudioMode audioMode;

    private double speechRate;
    private int listeningReadingSeconds;
    private int listeningTransferMinutes;
    private int dailyStudyMinutes;
    private int resolvedAfterPieces;
    private boolean onboardingDone;
    private Instant updatedAt;
}
