package com.ieltsprep.speaking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeakingResponseRepository extends JpaRepository<SpeakingResponse, Long> {

    List<SpeakingResponse> findBySessionIdOrderByIdAsc(Long sessionId);
}
