package com.ieltsprep.content;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** How often each Writing/Speaking topic from the taxonomy has been practised, for rotation. */
@Entity
@Table(name = "topic_usage")
@Getter
@Setter
public class TopicUsage {

    @Id
    private String topic;

    private int timesUsed;
    private Instant lastUsedAt;
}
