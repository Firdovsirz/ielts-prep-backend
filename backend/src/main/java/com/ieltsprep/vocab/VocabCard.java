package com.ieltsprep.vocab;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A flashcard with SM-2 scheduling fields. examples/collocations hold JSON arrays. */
@Entity
@Table(name = "vocab_cards")
@Getter
@Setter
public class VocabCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String word;
    private String partOfSpeech;
    private String definition;
    private String examples;
    private String collocations;
    private Integer awlSublist;
    private String cefr;
    private String topic;
    private String source;
    private String sourceRef;
    private String contextSentence;
    private double easeFactor = 2.5;
    private int intervalDays;
    private int repetitions;
    private int lapses;
    private Instant dueAt;
    private Instant lastReviewedAt;
    private String enrichmentStatus;
    private boolean suspended;
    private Instant createdAt;
}
