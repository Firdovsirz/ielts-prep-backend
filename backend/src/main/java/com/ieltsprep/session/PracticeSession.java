package com.ieltsprep.session;

import com.ieltsprep.content.Skill;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/** One practice/exam sitting: which items, timing, and the session-level score/band. */
@Entity
@Table(name = "sessions")
@Getter
@Setter
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Skill module;

    @Enumerated(EnumType.STRING)
    private SessionMode mode;

    @Enumerated(EnumType.STRING)
    private SessionKind kind;

    private Long mockTestId;
    private String itemIds;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer timeLimitSeconds;
    private Integer timeUsedSeconds;
    private Integer rawScore;
    private Integer maxScore;
    private BigDecimal bandEstimate;

    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    /** Small JSON document of session options (e.g. {"conversational": true}). */
    private String options;

    public List<Long> itemIdList() {
        if (itemIds == null || itemIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(itemIds.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    public void setItemIdList(List<Long> ids) {
        this.itemIds = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
