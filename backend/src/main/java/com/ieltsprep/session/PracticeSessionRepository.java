package com.ieltsprep.session;

import com.ieltsprep.content.Skill;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    List<PracticeSession> findByModuleAndStatusOrderByStartedAtAsc(Skill module, SessionStatus status);

    List<PracticeSession> findByStatusOrderByStartedAtAsc(SessionStatus status);

    List<PracticeSession> findByStartedAtGreaterThanEqualOrderByStartedAtAsc(Instant since);

    List<PracticeSession> findByMockTestId(Long mockTestId);

    List<PracticeSession> findTop50ByOrderByStartedAtDesc();
}
