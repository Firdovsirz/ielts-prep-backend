package com.ieltsprep.mock;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mock_tests")
@Getter
@Setter
public class MockTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** IN_PROGRESS, GRADING (all papers taken, Writing/Speaking bands pending), COMPLETED or ABANDONED. */
    private String status;
    /** LISTENING, READING, WRITING, SPEAKING, GRADING or DONE. */
    private String stage;

    private Long listeningSessionId;
    private Long readingSessionId;
    private Long writingSessionId;
    private Long speakingSessionId;
    private BigDecimal listeningBand;
    private BigDecimal readingBand;
    private BigDecimal writingBand;
    private BigDecimal speakingBand;
    private BigDecimal overallBand;
    /** JSON of {@link MockDtos.Report}, written once every band is known. */
    private String report;
    private Instant startedAt;
    private Instant finishedAt;
}
