package com.ieltsprep.errorlog;

import com.ieltsprep.content.Skill;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One tagged error from a graded Writing/Speaking attempt; feeds the Grammar module's error-driven practice. */
@Entity
@Table(name = "errors")
@Getter
@Setter
public class ErrorEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long attemptId;

    @Enumerated(EnumType.STRING)
    private Skill module;

    private String type;
    private String subtype;
    private String grammarArea;
    private String original;
    private String correction;
    private String explanation;
    private Instant createdAt;
    private Instant resolvedAt;
    private int drillCount;
    private Instant lastDrilledAt;
}
