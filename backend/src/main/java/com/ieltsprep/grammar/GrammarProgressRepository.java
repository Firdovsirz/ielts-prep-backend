package com.ieltsprep.grammar;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrammarProgressRepository extends JpaRepository<GrammarProgress, Long> {

    Optional<GrammarProgress> findByArea(String area);
}
