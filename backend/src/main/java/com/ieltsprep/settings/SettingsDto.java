package com.ieltsprep.settings;

import com.ieltsprep.content.ExamType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SettingsDto(
        ExamType examType,
        @DecimalMin("1.0") @DecimalMax("9.0") BigDecimal currentBand,
        @DecimalMin("1.0") @DecimalMax("9.0") BigDecimal targetBand,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true) LocalDate testDate,
        AudioMode audioMode,
        Double speechRate,
        Integer listeningReadingSeconds,
        Integer listeningTransferMinutes,
        Integer dailyStudyMinutes,
        Integer resolvedAfterPieces,
        Boolean onboardingDone) {

    public static SettingsDto of(UserSettings s) {
        return new SettingsDto(s.getExamType(), s.getCurrentBand(), s.getTargetBand(), s.getTestDate(), s.getAudioMode(),
                s.getSpeechRate(), s.getListeningReadingSeconds(), s.getListeningTransferMinutes(),
                s.getDailyStudyMinutes(), s.getResolvedAfterPieces(), s.isOnboardingDone());
    }
}
