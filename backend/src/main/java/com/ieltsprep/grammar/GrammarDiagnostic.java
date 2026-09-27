package com.ieltsprep.grammar;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grammar_diagnostics")
@Getter
@Setter
public class GrammarDiagnostic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String status;
    /** JSON {@link AdaptiveDiagnostic.State}. */
    private String state;
    private Instant startedAt;
    private Instant finishedAt;
}
