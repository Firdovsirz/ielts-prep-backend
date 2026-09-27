package com.ieltsprep.settings;

import com.ieltsprep.config.AppProperties;
import com.ieltsprep.content.ExamType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final long ID = 1L;

    private final UserSettingsRepository repo;
    private final AppProperties props;
    private final Clock clock;

    public SettingsService(UserSettingsRepository repo, AppProperties props, Clock clock) {
        this.repo = repo;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public UserSettings get() {
        return repo.findById(ID).orElseGet(this::createDefaults);
    }

    /**
     * Creates the settings row at start-up (see {@link SettingsBootstrap}) so that the first page load, which fetches
     * several endpoints in parallel, never races to insert it.
     */
    @Transactional
    public synchronized void ensureExists() {
        if (!repo.existsById(ID)) {
            createDefaults();
        }
    }

    @Transactional
    public UserSettings update(SettingsDto dto) {
        UserSettings s = get();
        if (dto.examType() != null && dto.examType() != ExamType.BOTH) {
            s.setExamType(dto.examType());
        }
        if (dto.currentBand() != null) {
            s.setCurrentBand(dto.currentBand());
        }
        if (dto.targetBand() != null) {
            s.setTargetBand(dto.targetBand());
        }
        s.setTestDate(dto.testDate());
        if (dto.audioMode() != null) {
            s.setAudioMode(dto.audioMode());
        }
        if (dto.speechRate() != null) {
            s.setSpeechRate(Math.max(0.6, Math.min(1.4, dto.speechRate())));
        }
        if (dto.listeningReadingSeconds() != null) {
            s.setListeningReadingSeconds(dto.listeningReadingSeconds());
        }
        if (dto.listeningTransferMinutes() != null) {
            s.setListeningTransferMinutes(dto.listeningTransferMinutes());
        }
        if (dto.dailyStudyMinutes() != null) {
            s.setDailyStudyMinutes(dto.dailyStudyMinutes());
        }
        if (dto.resolvedAfterPieces() != null) {
            s.setResolvedAfterPieces(Math.max(1, dto.resolvedAfterPieces()));
        }
        if (dto.onboardingDone() != null) {
            s.setOnboardingDone(dto.onboardingDone());
        }
        s.setUpdatedAt(Instant.now(clock));
        return repo.save(s);
    }

    private UserSettings createDefaults() {
        AppProperties.Defaults d = props.defaults();
        UserSettings s = new UserSettings();
        s.setId(ID);
        s.setExamType(d.examType() == null ? ExamType.ACADEMIC : d.examType());
        s.setCurrentBand(d.currentBand() == null ? new BigDecimal("6.0") : d.currentBand());
        s.setTargetBand(d.targetBand() == null ? new BigDecimal("7.5") : d.targetBand());
        s.setTestDate(d.testDate());
        s.setAudioMode(d.audioMode() == null ? AudioMode.BROWSER : d.audioMode());
        s.setSpeechRate(1.0);
        s.setListeningReadingSeconds(30);
        s.setListeningTransferMinutes(2); // computer-delivered test; 10 for paper-based (Settings)
        s.setDailyStudyMinutes(90);
        s.setResolvedAfterPieces(props.errorLog() == null ? 5 : props.errorLog().resolvedAfterPieces());
        s.setOnboardingDone(false);
        s.setUpdatedAt(Instant.now(clock));
        return repo.save(s);
    }
}
