package com.ieltsprep.vocab;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabReviewRepository extends JpaRepository<VocabReview, Long> {

    long countByReviewedAtGreaterThanEqual(Instant since);

    long countByReviewedAtBetween(Instant from, Instant to);
}
