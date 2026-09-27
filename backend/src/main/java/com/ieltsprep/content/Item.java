package com.ieltsprep.content;

import com.ieltsprep.common.Json;
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

/**
 * A generated (or seeded) practice item: passage + questions, listening script, writing/speaking prompt, grammar
 * lesson/exercise, word bank. {@code content} holds the full JSON document including answers; the API strips
 * answers before sending an item to the browser.
 */
@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Skill module;

    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    /** Buffer bucket within a task type: P1–P3, S1–S4, chart/essay/letter type, grammar area, topic. */
    private String variant;

    private String questionTypes;

    @Enumerated(EnumType.STRING)
    private ExamType examType;

    private Integer difficulty;
    private String cefr;
    private String topic;
    private String title;
    private String sourceUrl;
    private String licence;
    private String content;
    private String answerKey;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    private String verificationNotes;
    private int generationAttempts;

    @Enumerated(EnumType.STRING)
    private ItemOrigin origin;

    private String seedKey;
    private int timesServed;
    private Instant lastServedAt;
    private Instant createdAt;

    public <T> T contentAs(Class<T> type) {
        return Json.read(content, type);
    }
}
