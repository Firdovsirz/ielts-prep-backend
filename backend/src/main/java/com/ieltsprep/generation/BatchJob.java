package com.ieltsprep.generation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A Message Batches job in flight: GENERATE (drafts) or VERIFY (blind checks of drafts). */
@Entity
@Table(name = "batch_jobs")
@Getter
@Setter
public class BatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String anthropicBatchId;
    private String stage;
    private String taskType;
    private int requestCount;
    private String status;
    /** JSON: custom_id → {variant, source_url, licence} (GENERATE) or custom_id → item id (VERIFY). */
    private String details;
    private Instant createdAt;
    private Instant finishedAt;
}
