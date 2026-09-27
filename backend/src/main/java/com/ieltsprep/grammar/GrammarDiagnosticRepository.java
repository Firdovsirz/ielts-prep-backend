package com.ieltsprep.grammar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrammarDiagnosticRepository extends JpaRepository<GrammarDiagnostic, Long> {

    Optional<GrammarDiagnostic> findFirstByStatusOrderByIdDesc(String status);

    List<GrammarDiagnostic> findAllByOrderByIdDesc();
}
