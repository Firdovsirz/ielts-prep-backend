package com.ieltsprep.speaking;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One recorded answer: the question asked, the audio file and its transcript. */
@Entity
@Table(name = "speaking_responses")
@Getter
@Setter
public class SpeakingResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Long attemptId;
    /** 1, 2 (long turn and rounding-off) or 3. */
    private int part;
    private int questionIndex;
    private String question;
    private String audioPath;
    private String transcript;
    private Integer durationSeconds;
    private Instant createdAt;
}
