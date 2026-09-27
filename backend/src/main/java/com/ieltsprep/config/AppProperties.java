package com.ieltsprep.config;

import com.ieltsprep.settings.AudioMode;
import com.ieltsprep.content.ExamType;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ielts")
public record AppProperties(
        String dataDir,
        String promptsDir,
        String defaultsDir,
        String recordingsDir,
        boolean seedOnStartup,
        Admin admin,
        Security security,
        Defaults defaults,
        Buffer buffer,
        Coach coach,
        Speaking speaking,
        ErrorLog errorLog,
        Fetch fetch,
        Scheduling scheduling) {

    public record Scheduling(boolean enabled) {}

    public record Admin(String email, String password) {}

    public record Security(String jwtSecret, int tokenTtlHours, List<String> corsAllowedOrigins) {}

    public record Defaults(
            ExamType examType, BigDecimal currentBand, BigDecimal targetBand, LocalDate testDate, AudioMode audioMode) {}

    public record Buffer(boolean enabled, int minPerBucket, int intervalMinutes, int initialDelayMinutes) {}

    public record Coach(String cron) {}

    public record Speaking(String transcription, String whisperUrl, String whisperApiKey, String whisperModel) {
        public boolean whisperConfigured() {
            return whisperUrl != null && !whisperUrl.isBlank();
        }
    }

    public record ErrorLog(int resolvedAfterPieces) {}

    public record Fetch(String userAgent, long minDelayMs) {}

    public Path dataPath() {
        return Path.of(dataDir).toAbsolutePath().normalize();
    }

    /** Where Speaking recordings are stored (defaults to data/recordings). */
    public Path recordingsPath() {
        return recordingsDir == null || recordingsDir.isBlank() ? dataPath().resolve("recordings")
                : Path.of(recordingsDir).toAbsolutePath().normalize();
    }

    public Path promptsPath() {
        return Path.of(promptsDir).toAbsolutePath().normalize();
    }
}
