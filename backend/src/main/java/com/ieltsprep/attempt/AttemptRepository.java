package com.ieltsprep.attempt;

import com.ieltsprep.content.Skill;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findBySessionIdOrderByIdAsc(Long sessionId);

    List<Attempt> findByModuleOrderBySubmittedAtAsc(Skill module);

    List<Attempt> findByModuleAndStatusOrderBySubmittedAtAsc(Skill module, AttemptStatus status);

    List<Attempt> findBySubmittedAtGreaterThanEqualOrderBySubmittedAtAsc(Instant since);

    List<Attempt> findByStatus(AttemptStatus status);

    List<Attempt> findAllByOrderBySubmittedAtAsc();

    List<Attempt> findByModuleInAndStatusOrderBySubmittedAtDesc(List<Skill> modules, AttemptStatus status);
}
