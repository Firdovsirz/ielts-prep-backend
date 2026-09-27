package com.ieltsprep.session;

import com.ieltsprep.common.ApiException;
import com.ieltsprep.content.Skill;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private final PracticeSessionRepository sessions;
    private final Clock clock;

    public SessionService(PracticeSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public PracticeSession start(Skill module, SessionMode mode, SessionKind kind, List<Long> itemIds, Integer timeLimitSeconds,
            Long mockTestId) {
        PracticeSession s = new PracticeSession();
        s.setModule(module);
        s.setMode(mode);
        s.setKind(kind);
        s.setItemIdList(itemIds);
        s.setTimeLimitSeconds(timeLimitSeconds);
        s.setMockTestId(mockTestId);
        s.setStartedAt(Instant.now(clock));
        s.setStatus(SessionStatus.IN_PROGRESS);
        return sessions.save(s);
    }

    public PracticeSession get(long id) {
        return sessions.findById(id).orElseThrow(() -> ApiException.notFound("Session " + id));
    }

    public PracticeSession require(long id, Skill module) {
        PracticeSession s = get(id);
        if (s.getModule() != module) {
            throw ApiException.badRequest("Session " + id + " is not a " + module + " session");
        }
        return s;
    }

    /** Closes a session with its score. {@code timeUsedSeconds} from the client is capped by wall-clock time. */
    @Transactional
    public PracticeSession complete(PracticeSession s, Integer timeUsedSeconds, Integer raw, Integer max, BigDecimal band) {
        Instant now = Instant.now(clock);
        int wallClock = (int) Duration.between(s.getStartedAt(), now).toSeconds();
        s.setFinishedAt(now);
        s.setTimeUsedSeconds(timeUsedSeconds == null ? wallClock : Math.min(Math.max(0, timeUsedSeconds), Math.max(wallClock, 1)));
        s.setRawScore(raw);
        s.setMaxScore(max);
        s.setBandEstimate(band);
        s.setStatus(SessionStatus.COMPLETED);
        return sessions.save(s);
    }

    @Transactional
    public PracticeSession save(PracticeSession s) {
        return sessions.save(s);
    }

    public void ensureOpen(PracticeSession s) {
        if (s.getStatus() != SessionStatus.IN_PROGRESS) {
            throw ApiException.conflict("Session " + s.getId() + " has already been submitted");
        }
    }
}
